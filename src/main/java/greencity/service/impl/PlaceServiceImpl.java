package greencity.service.impl;

import static greencity.constant.AppConstant.CONSTANT_OF_FORMULA_HAVERSINE_KM;

import greencity.constant.AppConstant;
import greencity.constant.ErrorMessage;
import greencity.constant.LogMessage;
import greencity.dto.PageableDto;
import greencity.dto.discount.DiscountDto;
import greencity.dto.filter.FilterDistanceDto;
import greencity.dto.filter.FilterPlaceDto;
import greencity.dto.openhours.OpeningHoursDto;
import greencity.dto.place.*;
import greencity.entity.*;
import greencity.entity.enums.PlaceStatus;
import greencity.entity.enums.ROLE;
import greencity.exception.NotFoundException;
import greencity.exception.PlaceStatusException;
import greencity.repository.PlaceRepo;
import greencity.repository.options.PlaceFilter;
import greencity.service.*;
import greencity.util.DateTimeService;
import java.util.*;
import java.util.stream.Collectors;
import javax.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The class provides implementation of the {@code PlaceService}.
 */
@Slf4j
@Service
@AllArgsConstructor
public class PlaceServiceImpl implements PlaceService {
    private static final PlaceStatus APPROVED_STATUS = PlaceStatus.APPROVED;
    private PlaceRepo placeRepo;
    private ModelMapper modelMapper;
    private CategoryService categoryService;
    private UserService userService;
    private SpecificationService specificationService;
    private EmailService emailService;
    private DiscountService discountService;
    private OpenHoursService openingHoursService;
    private LocationService locationService;

    /**
     * {@inheritDoc}
     *
     * @author Roman Zahorui
     */
    @Override
    public PageableDto getPlacesByStatus(PlaceStatus placeStatus, Pageable pageable) {
        Page<Place> places = placeRepo.findAllByStatusOrderByModifiedDateDesc(placeStatus, pageable);
        List<AdminPlaceDto> list = places.stream()
            .map(place -> modelMapper.map(place, AdminPlaceDto.class))
            .collect(Collectors.toList());
        return new PageableDto(list, places.getTotalElements(), places.getPageable().getPageNumber());
    }

    /**
     * {@inheritDoc}
     *
     * @author Kateryna Horokh
     */
    @Transactional
    @Override
    public Place save(PlaceAddDto dto, String email) {
        log.info(LogMessage.IN_SAVE, dto.getName(), email);

        Category category = categoryService.findByName(dto.getCategory().getName());
        Place place = modelMapper.map(dto, Place.class);

        setUserToPlaceByEmail(email, place);
        place.setCategory(category);
        place.setLocation(modelMapper.map(dto.getLocation(), Location.class));
        saveDiscountWithPlaceAndCategory(place.getDiscounts(), category, place);
        saveOpeningHoursWithPlace(place.getOpeningHoursList(), place);
        return placeRepo.save(place);
    }

    /**
     * Method for getting {@link User} and set this {@link User} to place.
     *
     * @param email - String, user's email.
     * @param place - {@link Place} entity.
     * @return user - {@link User}.
     * @author Kateryna Horokh
     */
    private User setUserToPlaceByEmail(String email, Place place) {
        User user = userService.findByEmail(email).orElseThrow(
            () -> new NotFoundException(ErrorMessage.USER_NOT_FOUND_BY_EMAIL));
        place.setAuthor(user);

        if (place.getAuthor().getRole() == ROLE.ROLE_ADMIN || place.getAuthor().getRole() == ROLE.ROLE_MODERATOR) {
            place.setStatus(APPROVED_STATUS);
        }
        return user;
    }

    /**
     * Method for setting {@link Place} to set of {@link OpeningHours}.
     *
     * @param openingHoursSet - set of {@link OpeningHours}.
     * @param place           - {@link Place} entity.
     * @author Kateryna Horokh
     */
    private void saveOpeningHoursWithPlace(Set<OpeningHours> openingHoursSet, Place place) {
        openingHoursSet.forEach(h -> h.setPlace(place));
    }

    /**
     * Method for setting {@link Place} to set of {@link Discount}.
     *
     * @param discounts - set of {@link Discount}.
     * @param category  - {@link Category} entity.
     * @param place     - {@link Place} entity.
     * @author Kateryna Horokh
     */
    private void saveDiscountWithPlaceAndCategory(Set<Discount> discounts, Category category, Place place) {
        discounts.forEach(disc -> {
            Specification specification = specificationService.findByName(disc.getSpecification().getName());
            disc.setSpecification(specification);
            disc.setPlace(place);
            disc.setCategory(category);
        });
    }

    /**
     * {@inheritDoc}
     *
     * @author Kateryna Horokh
     */
    @Transactional
    @Override
    public Place update(PlaceUpdateDto dto) {
        log.info(LogMessage.IN_UPDATE, dto.getName());

        Category updatedCategory = categoryService.findByName(dto.getCategory().getName());
        Place updatedPlace = findById(dto.getId());
        locationService.update(updatedPlace.getLocation().getId(), modelMapper.map(dto.getLocation(), Location.class));
        updatedPlace.setName(dto.getName());
        updatedPlace.setCategory(updatedCategory);
        placeRepo.save(updatedPlace);

        updateOpening(dto.getOpeningHoursList(), updatedPlace);
        updateDiscount(dto.getDiscounts(), updatedCategory, updatedPlace);

        return updatedPlace;
    }

    /**
     * Method for updating set of {@link Discount} and save with new {@link Category} and {@link Place}.
     *
     * @param discountDtos    - set of {@link Discount}.
     * @param updatedCategory - {@link Category} entity.
     * @param updatedPlace    - {@link Place} entity.
     * @author Kateryna Horokh
     */
    private void updateDiscount(Set<DiscountDto> discountDtos, Category updatedCategory, Place updatedPlace) {
        log.info(LogMessage.IN_UPDATE_DISCOUNT_FOR_PLACE);

        Set<Discount> discountsOld = discountService.findAllByPlaceId(updatedPlace.getId());
        discountService.deleteAllByPlaceId(updatedPlace.getId());
        Set<Discount> discounts = new HashSet<>();
        discountDtos.forEach(d -> {
            Discount discount = modelMapper.map(d, Discount.class);
            Specification specification = specificationService.findByName(d.getSpecification().getName());
            discount.setPlace(updatedPlace);
            discount.setCategory(updatedCategory);
            discount.setSpecification(specification);
            discountService.save(discount);
            discounts.add(discount);
        });
        discountsOld.addAll(discounts);
    }

    /**
     * Method for updating set of {@link OpeningHours} and save with new {@link Place}.
     *
     * @param hoursUpdateDtoSet - set of {@link Discount}.
     * @param updatedPlace      - {@link Place} entity.
     * @author Kateryna Horokh
     */
    private void updateOpening(Set<OpeningHoursDto> hoursUpdateDtoSet, Place updatedPlace) {
        log.info(LogMessage.IN_UPDATE_OPENING_HOURS_FOR_PLACE);

        Set<OpeningHours> openingHoursSetOld = openingHoursService.findAllByPlaceId(updatedPlace.getId());
        openingHoursService.deleteAllByPlaceId(updatedPlace.getId());
        Set<OpeningHours> hours = new HashSet<>();
        hoursUpdateDtoSet.forEach(h -> {
            OpeningHours openingHours = modelMapper.map(h, OpeningHours.class);
            openingHours.setPlace(updatedPlace);
            openingHoursService.save(openingHours);
            hours.add(openingHours);
        });
        openingHoursSetOld.addAll(hours);
    }

    /**
     * {@inheritDoc}
     *
     * @author Nazar Vladyka
     */
    @Override
    public Long deleteById(Long id) {
        log.info(LogMessage.IN_DELETE_BY_ID, id);

        updateStatus(id, PlaceStatus.DELETED);

        return id;
    }

    /**
     * {@inheritDoc}
     *
     * @author Nazar Vladyka
     */
    @Override
    public Long bulkDelete(List<Long> ids) {
        List<UpdatePlaceStatusDto> deletedPlaces =
            updateStatuses(new BulkUpdatePlaceStatusDto(ids, PlaceStatus.DELETED));

        return (long) deletedPlaces.size();
    }

    /**
     * {@inheritDoc}
     *
     * @author Nazar Vladyka.
     */
    @Override
    public List<Place> findAll() {
        log.info(LogMessage.IN_FIND_ALL);
        return placeRepo.findAll();
    }

    /**
     * {@inheritDoc}
     *
     * @author Nazar Vladyka.
     */
    @Override
    public UpdatePlaceStatusDto updateStatus(Long id, PlaceStatus status) {
        log.info(LogMessage.IN_UPDATE_PLACE_STATUS, id, status);

        Place updatable = findById(id);
        if (!updatable.getStatus().equals(status)) {
            if (updatable.getStatus().equals(PlaceStatus.PROPOSED)) {
                emailService.sendChangePlaceStatusEmail(updatable, status);
            }
            updatable.setStatus(status);
            updatable.setModifiedDate(DateTimeService.getDateTime(AppConstant.UKRAINE_TIMEZONE));
        } else {
            log.error(LogMessage.PLACE_STATUS_NOT_DIFFERENT, id, status);
            throw new PlaceStatusException(
                updatable.getId() + ErrorMessage.PLACE_STATUS_NOT_DIFFERENT + updatable.getStatus());
        }

        return modelMapper.map(placeRepo.save(updatable), UpdatePlaceStatusDto.class);
    }

    /**
     * {@inheritDoc}
     *
     * @author Nazar Vladyka
     */
    @Transactional
    @Override
    public List<UpdatePlaceStatusDto> updateStatuses(BulkUpdatePlaceStatusDto dto) {
        List<UpdatePlaceStatusDto> updatedPlaces = new ArrayList<>();
        for (Long id : dto.getIds()) {
            updatedPlaces.add(updateStatus(id, dto.getStatus()));
        }

        return updatedPlaces;
    }

    /**
     * {@inheritDoc}
     *
     * @author Nazar Vladyka.
     */
    @Override
    public Place findById(Long id) {
        log.info(LogMessage.IN_FIND_BY_ID, id);
        return placeRepo
            .findById(id)
            .orElseThrow(() -> new NotFoundException(ErrorMessage.PLACE_NOT_FOUND_BY_ID + id));
    }

    /**
     * {@inheritDoc}
     *
     * @author Dmytro Dovhal
     */
    @Override
    public PlaceInfoDto getInfoById(Long id) {
        Place place =
            placeRepo
                .findById(id)
                .orElseThrow(() -> new NotFoundException(ErrorMessage.PLACE_NOT_FOUND_BY_ID + id));
        PlaceInfoDto placeInfoDto = modelMapper.map(place, PlaceInfoDto.class);
        placeInfoDto.setRate(placeRepo.getAverageRate(id));
        return placeInfoDto;
    }

    /**
     * {@inheritDoc}
     *
     * @author Kateryna Horokh
     */
    @Override
    public PlaceUpdateDto getInfoForUpdatingById(Long id) {
        Place place = placeRepo
            .findById(id)
            .orElseThrow(() -> new NotFoundException(ErrorMessage.PLACE_NOT_FOUND_BY_ID + id));
        PlaceUpdateDto placeUpdateDto = modelMapper.map(place, PlaceUpdateDto.class);
        return placeUpdateDto;
    }

    /**
     * {@inheritDoc}
     *
     * @author Marian Milian
     */
    @Override
    public List<PlaceByBoundsDto> findPlacesByMapsBounds(@Valid FilterPlaceDto filterPlaceDto) {
        List<Place> list = placeRepo.findAll(new PlaceFilter(filterPlaceDto));
        return list.stream()
            .map(place -> modelMapper.map(place, PlaceByBoundsDto.class))
            .collect(Collectors.toList());
    }

    private List<Long> getPlaceBoundsId(List<PlaceByBoundsDto> listB) {
        List<Long> result = new ArrayList<Long>();
        listB.forEach(el -> result.add(el.getId()));
        return result;
    }

    /**
     * {@inheritDoc}
     *
     * @author Zakhar Skaletskyi
     */
    @Override
    public boolean existsById(Long id) {
        log.info(LogMessage.IN_EXISTS_BY_ID, id);
        return placeRepo.existsById(id);
    }

    /**
     * {@inheritDoc}
     *
     * @author Zakhar Skaletskyi
     */
    @Override
    public Double averageRate(Long id) {
        log.info(LogMessage.IN_AVERAGE_RATE, id);
        return placeRepo.getAverageRate(id);
    }

    /**
     * {@inheritDoc}
     *
     * @author Roman Zahorui
     */
    @Override
    public List<PlaceByBoundsDto> getPlacesByFilter(FilterPlaceDto filterDto) {
        List<Place> list = placeRepo.findAll(new PlaceFilter(filterDto));
        list = getPlacesByDistanceFromUser(filterDto, list);
        return list.stream()
            .map(place -> modelMapper.map(place, PlaceByBoundsDto.class))
            .collect(Collectors.toList());
    }

    /**
     * Method that filtering places by distance.
     *
     * @param filterDto - {@link FilterPlaceDto} DTO.
     * @param placeList - {@link List} of {@link Place} that will be filtered.
     * @return {@link List} of {@link Place} - list of filtered {@link Place}s.
     * @author Nazar Stasyuk
     */
    private List<Place> getPlacesByDistanceFromUser(FilterPlaceDto filterDto, List<Place> placeList) {
        FilterDistanceDto distanceFromUserDto = filterDto.getDistanceFromUserDto();
        if (distanceFromUserDto != null
            && distanceFromUserDto.getLat() != null
            && distanceFromUserDto.getLng() != null
            && distanceFromUserDto.getDistance() != null) {
            placeList = placeList.stream().filter(place -> {
                double userLatRad = Math.toRadians(distanceFromUserDto.getLat());
                double userLngRad = Math.toRadians(distanceFromUserDto.getLng());
                double placeLatRad = Math.toRadians(place.getLocation().getLat());
                double placeLngRad = Math.toRadians(place.getLocation().getLng());

                double distance = CONSTANT_OF_FORMULA_HAVERSINE_KM * Math.acos(
                    Math.cos(userLatRad)
                        * Math.cos(placeLatRad)
                        * Math.cos(placeLngRad - userLngRad)
                        + Math.sin(userLatRad)
                        * Math.sin(placeLatRad));
                return distance <= distanceFromUserDto.getDistance();
            }).collect(Collectors.toList());
        }
        return placeList;
    }

    /**
     * {@inheritDoc}
     *
     * @author Rostyslav Khasanov
     */
    @Override
    public PageableDto<AdminPlaceDto> filterPlaceBySearchPredicate(FilterPlaceDto filterDto, Pageable pageable) {
        Page<Place> list = placeRepo.findAll(new PlaceFilter(filterDto), pageable);
        List<AdminPlaceDto> adminPlaceDtos =
            list.getContent().stream()
                .map(user -> modelMapper.map(user, AdminPlaceDto.class))
                .collect(Collectors.toList());
        return new PageableDto<AdminPlaceDto>(
            adminPlaceDtos,
            list.getTotalElements(),
            list.getPageable().getPageNumber());
    }

    /**
     * {@inheritDoc}
     *
     * @author Nazar Vladyka
     */
    @Override
    public List<PlaceStatus> getStatuses() {
        return Arrays.asList(PlaceStatus.class.getEnumConstants());
    }
}