package org.example.hotelreservation.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.DistanceUnit;
import co.elastic.clients.elasticsearch._types.GeoLocation;
import co.elastic.clients.elasticsearch._types.LatLonGeoLocation;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.json.JsonData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.hotelreservation.config.HotelProperties;
import org.example.hotelreservation.dto.HotelSearchQuery;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class HotelEsSearchService {

    private final ElasticsearchClient elasticsearchClient;
    private final HotelProperties properties;

    public List<Long> searchHotelIds(HotelSearchQuery query, int fetchSize) {
        try {
            BoolQuery.Builder bool = new BoolQuery.Builder();
            if (StringUtils.hasText(query.getCity())) {
                bool.filter(Query.of(q -> q.term(t -> t.field("city").value(query.getCity()))));
            }
            if (query.getStar() != null) {
                bool.filter(Query.of(q -> q.term(t -> t.field("starRating").value(query.getStar()))));
            }
            if (query.getMinPrice() != null || query.getMaxPrice() != null) {
                bool.filter(Query.of(q -> q.range(r -> r.untyped(u -> {
                    u.field("minPrice");
                    if (query.getMinPrice() != null) {
                        u.gte(JsonData.of(query.getMinPrice()));
                    }
                    if (query.getMaxPrice() != null) {
                        u.lte(JsonData.of(query.getMaxPrice()));
                    }
                    return u;
                }))));
            }
            if (StringUtils.hasText(query.getKeyword())) {
                bool.must(Query.of(q -> q.match(m -> m.field("name").query(query.getKeyword()))));
            }
            if (query.getLatitude() != null && query.getLongitude() != null) {
                double km = query.getRadiusKm() == null ? 8.0 : query.getRadiusKm();
                bool.filter(Query.of(q -> q.geoDistance(g -> g
                        .field("location")
                        .distance(km + DistanceUnit.Kilometers.jsonValue())
                        .location(GeoLocation.of(l -> l.latlon(LatLonGeoLocation.of(p -> p
                                .lat(query.getLatitude())
                                .lon(query.getLongitude()))))))));
            }
            SearchResponse<HotelDocument> response = elasticsearchClient.search(s -> s
                            .index(properties.getSearch().getIndex())
                            .query(Query.of(q -> q.bool(bool.build())))
                            .sort(so -> so.field(f -> f.field("minPrice").order(SortOrder.Asc)))
                            .size(fetchSize),
                    HotelDocument.class);
            List<Long> ids = new ArrayList<>();
            response.hits().hits().forEach(hit -> {
                if (hit.source() != null && hit.source().getHotelId() != null) {
                    ids.add(hit.source().getHotelId());
                }
            });
            return ids;
        } catch (Exception ex) {
            log.warn("elasticsearch search failed, caller should fallback: {}", ex.getMessage());
            return null;
        }
    }
}
