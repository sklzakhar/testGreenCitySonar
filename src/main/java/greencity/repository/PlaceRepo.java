package greencity.repository;

import greencity.entity.Place;
import greencity.entity.enums.PlaceStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Provides an interface to manage {@link Place} entity.
 */
@Repository
public interface PlaceRepo extends JpaRepository<Place, Long>, JpaSpecificationExecutor<Place> {
    /**
     * Finds all places related to the given {@code PlaceStatus}.
     *
     * @param status   to find by.
     * @param pageable pageable configuration.
     * @return a list of places with the given {@code PlaceStatus}.
     * @author Roman Zahorui
     */
    Page<Place> findAllByStatusOrderByModifiedDateDesc(PlaceStatus status, Pageable pageable);

    /**
     * Method to find average rate.
     *
     * @param id place
     * @return average rate
     */
    @Query(value = "select avg(r.rate) FROM Rate r " + "where place_id = :id")
    Double getAverageRate(@Param("id") Long id);

    /**
     * Generated javadoc, must be replaced with real one.
     */
    @Query("from Place p where p.status = :status")
    List<Place> getPlacesByStatus(@Param("status") PlaceStatus status);

    /**
     * Method return a list {@code Place} depends on the map bounds.
     *
     * @param northEastLat latitude of extreme North-East point of the map.
     * @param northEastLng longitude of extreme North-East point of the map.
     * @param southWestLat latitude of South-West point of the map.
     * @param southWestLng longitude of South-West point of the map.
     * @param status       status of places witch should be presented.
     * @return a list of {@code Place}
     * @author Marian Milian.
     */
    @Query(
        value =
            "FROM Place p "
                + " left join"
                + " Location l  on p.location.id =l.id "
                + " where l.lat > :southWestLat  and l.lat< :northEastLat"
                + " AND l.lng >:southWestLng and l.lng<:northEastLng "
                + " and p.status = :status"
                + " ORDER BY p.name")
    List<Place> findPlacesByMapsBounds(
        @Param("northEastLat") Double northEastLat,
        @Param("northEastLng") Double northEastLng,
        @Param("southWestLat") Double southWestLat,
        @Param("southWestLng") Double southWestLng,
        @Param("status") PlaceStatus status
    );
}
