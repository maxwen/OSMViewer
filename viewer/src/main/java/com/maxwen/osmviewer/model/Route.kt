package com.maxwen.osmviewer.model

import com.github.cliftonlabs.json_simple.JsonArray
import com.github.cliftonlabs.json_simple.JsonObject
import com.maxwen.osmviewer.QueryController
import com.maxwen.osmviewer.shared.GISUtils
import com.maxwen.osmviewer.shared.LogUtils
import com.maxwen.osmviewer.shared.OSMUtils
import com.maxwen.osmviewer.shared.RouteUtils
import javafx.scene.paint.Color

class Route(val startPoint: RoutingNode, val endPoint: RoutingNode, val type: RouteUtils.TYPE) {
    private var mCoords = JsonArray()
    private var mEdgeIdList = JsonArray()
    private var mWayIdList = JsonArray()
    private var mStreetTypeMap = JsonObject()

    constructor(
        startPoint: RoutingNode,
        endPoint: RoutingNode,
        type: RouteUtils.TYPE,
        edgeIdList: JsonArray,
        wayIdList: JsonArray,
        streetTypeMap: JsonObject,
        coords: JsonArray
    ) : this(startPoint, endPoint, type) {
        mEdgeIdList = edgeIdList
        mWayIdList = wayIdList
        mStreetTypeMap = streetTypeMap
        mCoords = coords
    }

    companion object {
        fun getRouteColor(type: RouteUtils.TYPE): Color {
            when (type) {
                RouteUtils.TYPE.FASTEST -> return Color.RED
                RouteUtils.TYPE.ALT -> return Color.GREEN
                RouteUtils.TYPE.SHORTEST -> return Color.BLUE
                else ->  return Color.BLACK
            }
        }
        fun getRouteCSSColor(type: RouteUtils.TYPE): String {
            when (type) {
                RouteUtils.TYPE.FASTEST -> return "red"
                RouteUtils.TYPE.ALT -> return "green"
                RouteUtils.TYPE.SHORTEST -> return "blue"
                else ->  return "black"
            }
        }

    }

    fun getCoords(): JsonArray {
        return mCoords
    }

    fun getEdgeIdList(): JsonArray {
        return mEdgeIdList
    }

    fun getWayIdList(): JsonArray {
        return mWayIdList
    }

    fun getStreetTypeMap(): JsonObject {
        return mStreetTypeMap
    }

    fun setEdgeList(edgeList: JsonArray) {
        mEdgeIdList = edgeList
        analyzeRoute()
        QueryController.getInstance().addRoute(this)
    }

    private fun analyzeRoute() {
        LogUtils.log("analyzeRoute")

        var lastRef: Long = 0
        var revertFirstEdge = false

        mStreetTypeMap.put("0", 0.0)
        mStreetTypeMap.put("1", 0.0)
        mStreetTypeMap.put("2", 0.0)
        mStreetTypeMap.put("3", 0.0)
        mStreetTypeMap.put("4", 0.0)
        mStreetTypeMap.put("5", 0.0)
        mStreetTypeMap.put("6", 0.0)

        mWayIdList = JsonArray()
        var lastWayId = -1L
        var lastWayString = ""
        var wayLength = 0L
        var lastWayEntry = JsonObject()
        var lastStreetType = -1L

        mCoords.clear()

        // find where first and second edge meet to know which way to start
        if (mEdgeIdList.size > 1) {
            val firstEdge = QueryController.getInstance().getEdgeEntryForId(GISUtils.getLongValue(mEdgeIdList[0]))
            val secondEdge = QueryController.getInstance().getEdgeEntryForId(GISUtils.getLongValue(mEdgeIdList[1]))
            if (firstEdge != null && secondEdge != null) {
                if (firstEdge["startRef"] == secondEdge["startRef"] || firstEdge["startRef"] == secondEdge["endRef"]) {
                    revertFirstEdge = true
                }
            } else {
                LogUtils.error("Failed to resolve first two edges from route " + mEdgeIdList.subList(0, 2))
            }
        }

        for (i in mEdgeIdList.indices) {
            val edgeId = GISUtils.getLongValue(mEdgeIdList[i])
            val edge = QueryController.getInstance().getEdgeEntryForId(edgeId)
            if (edge != null) {
                var reverseEdge = false
                var coords = edge["coords"] as JsonArray?
                if (i == 0 && revertFirstEdge || lastRef != 0L && GISUtils.getLongValue(edge["startRef"]) != lastRef) {
                    // reverse
                    coords = OSMUtils.reverseCoordsArray(coords)
                    reverseEdge = true
                }
                // first point is the same as last one of prev edge
                val start = if (i == 0) 0 else 1
                for (j in start until coords!!.size) {
                    mCoords.add(coords[j])
                }
                lastRef = GISUtils.getLongValue(if (reverseEdge) edge["startRef"] else edge["endRef"])

                val edgeLength = GISUtils.getLongValue(edge["length"])
                val streetType = GISUtils.getLongValue(edge["streetType"])

                wayLength += edgeLength

                val key = streetType.toString()
                val streetTypeLength = GISUtils.getDoubleValue(mStreetTypeMap.get(key)!!)
                mStreetTypeMap.put(key, streetTypeLength + edgeLength)

                val wayId = GISUtils.getLongValue(edge["wayId"])
                if (wayId != lastWayId || streetType != lastStreetType) {
                    val way = QueryController.getInstance().getWayEntryForId(wayId)
                    var wayString = ""
                    if (way.containsKey("name")) {
                        wayString += way.get("name").toString()
                    }
                    if (way.containsKey("ref")) {
                        if (wayString.isNotEmpty()) {
                            wayString += " / "
                        }
                        wayString += way.get("ref")
                    }
                    if (wayString.isNotEmpty() && lastWayString != wayString) {
                        val wayEntry = JsonObject()
                        wayEntry.put("wayId", wayId)
                        wayEntry.put("name", wayString)
                        wayEntry.put("length", 0)
                        wayEntry.put("pos", coords[0])
                        wayEntry.put("streetType", streetType)

                        mWayIdList.add(wayEntry)

                        lastWayEntry.put("length", wayLength)
                        lastWayString = wayString
                        lastWayEntry = wayEntry
                        wayLength = 0
                        lastStreetType = streetType
                    }
                    lastWayId = wayId
                }
            } else {
                LogUtils.error("Failed to resolve edge from route edgeId = $edgeId")
            }
        }
    }
}