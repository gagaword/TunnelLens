package com.gagaworld.modernsocks.vpn

internal data class VpnIpv4Route(
    val address: String,
    val prefixLength: Int,
)

/** Builds a portable IPv4 route set for every supported Android API level. */
internal object VpnIpv4RoutePolicy {
    private val allTraffic = Ipv4Cidr.parse("0.0.0.0", 0)
    private val localNetworks = listOf(
        Ipv4Cidr.parse("10.0.0.0", 8),
        Ipv4Cidr.parse("127.0.0.0", 8),
        Ipv4Cidr.parse("169.254.0.0", 16),
        Ipv4Cidr.parse("172.16.0.0", 12),
        Ipv4Cidr.parse("192.168.0.0", 16),
        Ipv4Cidr.parse("224.0.0.0", 4),
        Ipv4Cidr.parse("255.255.255.255", 32),
    )

    fun routes(bypassLocalNetworks: Boolean): List<VpnIpv4Route> {
        if (!bypassLocalNetworks) return listOf(allTraffic.toVpnRoute())
        return localNetworks
            .fold(listOf(allTraffic)) { routes, excluded ->
                routes.flatMap { route -> route.subtract(excluded) }
            }
            .sortedWith(compareBy(Ipv4Cidr::network, Ipv4Cidr::prefixLength))
            .map(Ipv4Cidr::toVpnRoute)
    }

    internal fun routesAddress(routes: List<VpnIpv4Route>, address: String): Boolean {
        val destination = Ipv4Cidr.parse(address, 32)
        return routes.any { route ->
            Ipv4Cidr.parse(route.address, route.prefixLength).contains(destination)
        }
    }
}

private data class Ipv4Cidr(
    val network: Long,
    val prefixLength: Int,
) {
    fun contains(other: Ipv4Cidr): Boolean =
        prefixLength <= other.prefixLength && network == other.network.and(mask(prefixLength))

    fun subtract(excluded: Ipv4Cidr): List<Ipv4Cidr> {
        if (excluded.contains(this)) return emptyList()
        if (!contains(excluded)) return listOf(this)
        check(prefixLength < 32)
        val childPrefix = prefixLength + 1
        val childSize = 1L shl (32 - childPrefix)
        return listOf(
            Ipv4Cidr(network, childPrefix),
            Ipv4Cidr(network + childSize, childPrefix),
        ).flatMap { child -> child.subtract(excluded) }
    }

    fun toVpnRoute(): VpnIpv4Route = VpnIpv4Route(
        address = listOf(24, 16, 8, 0).joinToString(".") { shift ->
            ((network shr shift) and 0xff).toString()
        },
        prefixLength = prefixLength,
    )

    companion object {
        fun parse(address: String, prefixLength: Int): Ipv4Cidr {
            require(prefixLength in 0..32)
            val octets = address.split('.')
            require(octets.size == 4)
            val value = octets.fold(0L) { result, octet ->
                val number = octet.toInt()
                require(number in 0..255)
                (result shl 8) or number.toLong()
            }
            val network = value.and(mask(prefixLength))
            require(value == network) { "IPv4 route must use a canonical network address" }
            return Ipv4Cidr(network, prefixLength)
        }

        private fun mask(prefixLength: Int): Long = when (prefixLength) {
            0 -> 0L
            else -> (0xffffffffL shl (32 - prefixLength)).and(0xffffffffL)
        }
    }
}
