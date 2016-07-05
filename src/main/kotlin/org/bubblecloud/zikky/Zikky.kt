package org.bubblecloud.zikky

import org.bubblecloud.zigbee.v3.ZigBeeGatewayClient
import org.bubblecloud.zigbee.v3.zcl.protocol.command.ias.zone.ZoneStatusChangeNotificationCommand
import kotlin.concurrent.fixedRateTimer

fun main(args : Array<String>) {
    val api = ZigBeeGatewayClient("http://127.0.0.1:5000/", "secret")

    println("Zikky startup...")
    api.startup()
    println("Zikky startup.")

    onExit {
        println("Zikky shutdown...")
        api.shutdown()
        println("Zikky shutdown.")
    }

    val devices = api.devices.filter { it.label != null }.associateBy({it.label}, {it})
    val groups = api.groups.associateBy({it.label}, {it})

    val zone = devices["zone"]!!
    val lamps = groups["lamps"]!!

    var movement = false
    var lighting = false
    var changeTime = currentTime()

    api.addCommandListener {
        if (it is ZoneStatusChangeNotificationCommand) {
            val command: ZoneStatusChangeNotificationCommand = it

            if (command.sourceAddress == zone.networkAddress && command.sourceEnpoint == zone.endpoint) {
                val alarm1 = command.zoneStatus and (1 shl 0) > 0
                val alarm2 = command.zoneStatus and (1 shl 1) > 0
                movement = alarm1 || alarm2
                changeTime = currentTime()
                if (movement) {
                    println("Zone movement detected.")
                } else {
                    println("Zone movement not detected.")
                }
            }
        }
    }

    fixedRateTimer("Lighting timer", true, 0, 1000, {
        if (movement != lighting) {
            if (movement) {
                api.on(lamps)
                lighting = true
                changeTime = currentTime()
                println("Occupied, switched lamps on.")
            } else {
                if (currentTime() - changeTime > 15 * 60000) {
                    api.off(lamps)
                    lighting = false
                    changeTime = currentTime()
                    println("Unoccupied, switched lamps off.")
                }
            }
        }
    })

}

fun currentTime(): Long {
    return System.currentTimeMillis()
}

fun onExit(shutdownHook: () -> Unit) {
    Runtime.getRuntime().addShutdownHook(Thread(shutdownHook))
}
