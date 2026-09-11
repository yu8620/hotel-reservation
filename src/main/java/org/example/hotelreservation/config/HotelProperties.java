package org.example.hotelreservation.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "hotel")
public class HotelProperties {

    private Jwt jwt = new Jwt();
    private Order order = new Order();
    private Inventory inventory = new Inventory();
    private Search search = new Search();

    @Data
    public static class Jwt {
        private String secret;
        private int expireHours = 24;
    }

    @Data
    public static class Order {
        private int payTimeoutMinutes = 15;
        private int freeCancelHours = 24;
    }

    @Data
    public static class Inventory {
        private int calendarDays = 90;
    }

    @Data
    public static class Search {
        private String index = "hotels";
    }
}
