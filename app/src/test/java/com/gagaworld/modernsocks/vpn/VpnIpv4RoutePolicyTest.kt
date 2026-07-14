package com.gagaworld.modernsocks.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnIpv4RoutePolicyTest {
    @Test
    fun disabled_routesAllIpv4Traffic() {
        val routes = VpnIpv4RoutePolicy.routes(bypassLocalNetworks = false)

        assertEquals(listOf(VpnIpv4Route("0.0.0.0", 0)), routes)
        assertTrue(VpnIpv4RoutePolicy.routesAddress(routes, "10.0.0.1"))
        assertTrue(VpnIpv4RoutePolicy.routesAddress(routes, "8.8.8.8"))
    }

    @Test
    fun enabled_excludesPrivateLoopbackLinkLocalMulticastAndBroadcast() {
        val routes = VpnIpv4RoutePolicy.routes(bypassLocalNetworks = true)

        listOf(
            "10.0.0.1",
            "127.0.0.1",
            "169.254.12.34",
            "172.16.0.1",
            "172.31.255.254",
            "192.168.1.1",
            "224.0.0.251",
            "239.255.255.250",
            "255.255.255.255",
        ).forEach { address ->
            assertFalse(address, VpnIpv4RoutePolicy.routesAddress(routes, address))
        }
    }

    @Test
    fun enabled_stillRoutesPublicAndAdjacentIpv4Traffic() {
        val routes = VpnIpv4RoutePolicy.routes(bypassLocalNetworks = true)

        listOf(
            "1.1.1.1",
            "9.255.255.255",
            "11.0.0.0",
            "126.255.255.255",
            "128.0.0.0",
            "169.253.255.255",
            "169.255.0.0",
            "172.15.255.255",
            "172.32.0.0",
            "192.167.255.255",
            "192.169.0.0",
            "223.255.255.255",
            "240.0.0.1",
            "255.255.255.254",
        ).forEach { address ->
            assertTrue(address, VpnIpv4RoutePolicy.routesAddress(routes, address))
        }
    }

    @Test
    fun enabled_producesUniqueCanonicalRoutes() {
        val routes = VpnIpv4RoutePolicy.routes(bypassLocalNetworks = true)

        assertTrue(routes.isNotEmpty())
        assertEquals(routes.size, routes.distinct().size)
        assertFalse(routes.contains(VpnIpv4Route("0.0.0.0", 0)))
    }
}
