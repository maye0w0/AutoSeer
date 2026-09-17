package com.autoseer.libautomata

/**
 * An in-memory image (a screenshot or a template to search for). The concrete
 * implementation lives in the platform layer (e.g. an OpenCV `Mat` wrapper in
 * the `app` module); `libautomata` only depends on this abstraction.
 */
interface IPattern : AutoCloseable {
    val width: Int
    val height: Int
    val size: Size get() = Size(width, height)

    /** Return a new pattern containing only [region] of this image. */
    fun crop(region: Region): IPattern

    /** Return a new pattern resized to [size]. */
    fun resize(size: Size): IPattern

    /** Persist to [path] as PNG (debugging / capturing template assets). */
    fun save(path: String)

    override fun close() {}
}
