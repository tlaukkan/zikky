package org.bubblecloud.zikky

import org.bubblecloud.zigbee.v3.ZigBeeGatewayClient
import org.bubblecloud.zigbee.v3.ZigBeeGroup
import org.bubblecloud.zigbee.v3.zcl.protocol.ZclClusterType
import org.bubblecloud.zigbee.v3.zcl.protocol.command.general.ReportAttributesCommand
import org.bubblecloud.zigbee.v3.zcl.protocol.command.ias.zone.ZoneStatusChangeNotificationCommand
import kotlin.concurrent.fixedRateTimer

fun main(args : Array<String>) {
    println("Zikky startup...")
    val api = ZigBeeGatewayClient("http://127.0.0.1:5000/", "secret")
    api.startup()
    println("Zikky startup.")

    onExit {
        println()
        println("Zikky shutdown...")
        api.shutdown()
        println("Zikky shutdown.")
    }

    var devices = api.zigBeeDevices.filter { it.label != null }.associateBy({it.label}, {it})
    var lamps = ZigBeeGroup(0)

    println()
    println("Labeled devices in network:")

    for ((index, device) in devices.values.withIndex()) {
        val label: String
        if (device.label != null) {
            label = device.label
        } else {
            label = "<no label>"
        }
        print(index.toString().padStart(3))
        print(") ")
        print(device.networkAddress.toString().padEnd(6))
        print(": ")
        print(label)
        println()
    }

    var occupied = false
    var lighting = false
    var lastStateChangeTimeMillis = System.currentTimeMillis()

    api.addCommandListener {
        if (it is ZoneStatusChangeNotificationCommand) {
            val command: ZoneStatusChangeNotificationCommand = it
            val alarm1Mask = 1 shl 0
            val alarm2Mask = 1 shl 1
            val alarm1 = command.zoneStatus and alarm1Mask > 0
            val alarm2 = command.zoneStatus and alarm2Mask > 0
            println("Zone status alarm 1: $alarm1 alarm 2: $alarm2")

            occupied = alarm1 || alarm2
            lastStateChangeTimeMillis = System.currentTimeMillis()
        }
    }

    fixedRateTimer("light-control", true, 0, 1000, {
        if (occupied != lighting) {
            if (occupied) {
                api.on(lamps)
                lighting = true
                lastStateChangeTimeMillis = System.currentTimeMillis()
                println("Occupied, switched lamps on.")
            } else {
                if (System.currentTimeMillis() - lastStateChangeTimeMillis > 15000) {
                    api.off(lamps)
                    lighting = false
                    lastStateChangeTimeMillis = System.currentTimeMillis()
                    println("Unoccupied, switched lamps off.")
                } else {
                    println("Unoccupied, waiting to switch lamps off.")
                }
            }
        }
    })

}

fun onExit(shutdownHook: () -> Unit) {
    Runtime.getRuntime().addShutdownHook(Thread(shutdownHook))
}
