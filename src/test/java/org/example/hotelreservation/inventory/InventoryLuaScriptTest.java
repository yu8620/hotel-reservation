package org.example.hotelreservation.inventory;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryLuaScriptTest {

    @Test
    void deductScriptChecksEveryNightBeforeDecrement() throws Exception {
        String lua = new String(
                getClass().getClassLoader().getResourceAsStream("lua/deduct_inventory.lua").readAllBytes(),
                StandardCharsets.UTF_8);
        int lastGet = lua.lastIndexOf("GET");
        int firstDecr = lua.indexOf("DECRBY");
        assertTrue(lastGet > 0 && firstDecr > lastGet,
                "必须先遍历校验所有日期，再 DECRBY，否则会出现扣了 20 号、21 号失败的半成功");
    }

    @Test
    void restoreScriptOnlyIncrements() throws Exception {
        String lua = new String(
                getClass().getClassLoader().getResourceAsStream("lua/restore_inventory.lua").readAllBytes(),
                StandardCharsets.UTF_8);
        assertTrue(lua.contains("INCRBY"));
        assertTrue(!lua.contains("DECRBY"));
    }
}
