package com.autoseer.libautomata

/** A size in pixels. */
data class Size(val width: Int, val height: Int) {
    val area: Int get() = width * height
}

/**
 * A point in a coordinate space. All script-facing coordinates live in the
 * *normalized* 720p space; [transform] converts between coordinate spaces
 * (e.g. normalized -> device) so the same scripts work on any resolution.
 */
data class Location(val x: Int, val y: Int) {
    operator fun plus(other: Location) = Location(x + other.x, y + other.y)
    operator fun minus(other: Location) = Location(x - other.x, y - other.y)

    /** Map this point from coordinate space [from] into coordinate space [to]. */
    fun transform(from: Size, to: Size): Location {
        if (from.width == 0 || from.height == 0) return this
        return Location(
            x = (x.toDouble() * to.width / from.width).toInt(),
            y = (y.toDouble() * to.height / from.height).toInt(),
        )
    }
}

/** A rectangular region. Top-left is ([x], [y]); grows right/down. */
data class Region(val x: Int, val y: Int, val width: Int, val height: Int) {
    val right: Int get() = x + width
    val bottom: Int get() = y + height
    val center: Location get() = Location(x + width / 2, y + height / 2)
    val location: Location get() = Location(x, y)
    val size: Size get() = Size(width, height)

    fun contains(loc: Location): Boolean =
        loc.x in x until right && loc.y in y until bottom

    fun transform(from: Size, to: Size): Region {
        val topLeft = location.transform(from, to)
        val bottomRight = Location(right, bottom).transform(from, to)
        return Region(
            topLeft.x,
            topLeft.y,
            bottomRight.x - topLeft.x,
            bottomRight.y - topLeft.y,
        )
    }

    companion object {
        fun of(size: Size) = Region(0, 0, size.width, size.height)
    }
}

/** Result of a template match: where it was found and how confident (0..1). */
data class Match(val region: Region, val score: Double)
