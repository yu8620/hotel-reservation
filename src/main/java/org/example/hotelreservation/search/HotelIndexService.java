package org.example.hotelreservation.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.hotelreservation.config.HotelProperties;
import org.example.hotelreservation.entity.Hotel;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class HotelIndexService {

    private final ElasticsearchClient elasticsearchClient;
    private final HotelProperties properties;

    public void createIndexIfAbsent() {
        String index = properties.getSearch().getIndex();
        try {
            boolean exists = elasticsearchClient.indices().exists(e -> e.index(index)).value();
            if (exists) {
                return;
            }
            elasticsearchClient.indices().create(CreateIndexRequest.of(c -> c
                    .index(index)
                    .mappings(m -> m
                            .properties("hotelId", Property.of(p -> p.long_(l -> l)))
                            .properties("name", Property.of(p -> p.text(t -> t)))
                            .properties("city", Property.of(p -> p.keyword(k -> k)))
                            .properties("starRating", Property.of(p -> p.integer(i -> i)))
                            .properties("minPrice", Property.of(p -> p.double_(d -> d)))
                            .properties("address", Property.of(p -> p.text(t -> t)))
                            .properties("amenities", Property.of(p -> p.text(t -> t)))
                            .properties("location", Property.of(p -> p.geoPoint(g -> g)))
                    )));
            log.info("created elasticsearch index {}", index);
        } catch (Exception ex) {
            log.warn("elasticsearch unavailable, search will fall back to mysql: {}", ex.getMessage());
        }
    }

    public void rebuild(List<Hotel> hotels) {
        createIndexIfAbsent();
        String index = properties.getSearch().getIndex();
        try {
            if (elasticsearchClient.indices().exists(e -> e.index(index)).value()) {
                elasticsearchClient.indices().delete(d -> d.index(index));
            }
            createIndexIfAbsent();
            if (hotels.isEmpty()) {
                return;
            }
            BulkRequest.Builder bulk = new BulkRequest.Builder();
            for (Hotel hotel : hotels) {
                HotelDocument doc = toDocument(hotel);
                bulk.operations(op -> op.index(idx -> idx
                        .index(index)
                        .id(String.valueOf(hotel.getId()))
                        .document(doc)));
            }
            elasticsearchClient.bulk(bulk.build());
            log.info("indexed {} hotels into elasticsearch", hotels.size());
        } catch (Exception ex) {
            log.warn("rebuild elasticsearch failed: {}", ex.getMessage());
            throw new IllegalStateException("ES 不可用：" + ex.getMessage());
        }
    }

    public void indexOne(Hotel hotel) {
        try {
            createIndexIfAbsent();
            elasticsearchClient.index(i -> i
                    .index(properties.getSearch().getIndex())
                    .id(String.valueOf(hotel.getId()))
                    .document(toDocument(hotel)));
        } catch (Exception ex) {
            log.warn("index hotel {} failed: {}", hotel.getId(), ex.getMessage());
        }
    }

    public HotelDocument toDocument(Hotel hotel) {
        HotelDocument doc = new HotelDocument();
        doc.setHotelId(hotel.getId());
        doc.setName(hotel.getName());
        doc.setCity(hotel.getCity());
        doc.setStarRating(hotel.getStarRating());
        doc.setMinPrice(hotel.getMinPrice());
        doc.setAddress(hotel.getAddress());
        doc.setAmenities(hotel.getAmenities());
        HotelDocument.GeoPoint point = new HotelDocument.GeoPoint();
        point.setLat(hotel.getLatitude().doubleValue());
        point.setLon(hotel.getLongitude().doubleValue());
        doc.setLocation(point);
        return doc;
    }
}
