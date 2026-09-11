package org.example.hotelreservation;

import org.junit.jupiter.api.Test;

class HotelReservationApplicationTests {

    @Test
    void unitTestsDoNotNeedSpringContext() {
        // 全量启动依赖 MySQL/Redis/RabbitMQ/ES，见 README 用 docker compose 启动后再手动验证下单链路。
    }
}
