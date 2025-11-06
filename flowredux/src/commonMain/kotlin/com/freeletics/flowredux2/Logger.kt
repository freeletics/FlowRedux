package com.freeletics.flowredux2

import com.freeletics.flowredux2.Logger.Level

/**
 * Provides logging capabilities for state machines.
 */
public interface Logger {
    /**
     * The minimum log level that this logger logs.
     */
    public var minLevel: Level

    /**
     * Log the given [message] and or [throwable] to this logger's destination.
     *
     * [log] will always be called with a [level] that is at least [minLevel]
     */
    public fun log(tag: String, level: Level, message: String?, throwable: Throwable?)

    /**
     * The priority level for a message.
     */
    public enum class Level {
        Verbose,
        Debug,
        Info,
        Warn,
        Error,
    }
}

/**
 * A logger that logs to stdout.
 */
public class SimpleLogger(
    override var minLevel: Level,
) : Logger {
    override fun log(tag: String, level: Level, message: String?, throwable: Throwable?) {
        val prefix = "[${level.name.first()}] $tag"
        if (message != null && throwable != null) {
            println("$prefix: $message - ${throwable.message}")
        } else if (message != null) {
            println("$prefix: $message ")
        } else if (throwable != null) {
            println("$prefix: ${throwable.message}")
        }
        throwable?.printStackTrace()
    }
}

internal class TaggedLogger(
    private val logger: Logger,
    internal val tag: String,
) : Logger by logger

internal fun TaggedLogger.wrap(name: String) = TaggedLogger(this, "$tag -> $name")

private inline fun TaggedLogger?.logConditionally(level: Level, throwable: Throwable?, message: () -> String) {
    if (this != null && level >= minLevel) {
        log(tag, level, message(), throwable)
    }
}

internal inline fun TaggedLogger?.logV(message: () -> String) {
    logConditionally(Level.Verbose, null, message)
}

internal inline fun TaggedLogger?.logD(message: () -> String) {
    logConditionally(Level.Debug, null, message)
}

internal inline fun TaggedLogger?.logI(message: () -> String) {
    logConditionally(Level.Info, null, message)
}

internal inline fun TaggedLogger?.logW(message: () -> String) {
    logConditionally(Level.Warn, null, message)
}

internal inline fun TaggedLogger?.logE(message: () -> String) {
    logConditionally(Level.Error, null, message)
}

internal inline fun TaggedLogger?.logV(throwable: Throwable, message: () -> String) {
    logConditionally(Level.Verbose, throwable, message)
}

internal inline fun TaggedLogger?.logD(throwable: Throwable, message: () -> String) {
    logConditionally(Level.Debug, throwable, message)
}

internal inline fun TaggedLogger?.logI(throwable: Throwable, message: () -> String) {
    logConditionally(Level.Info, throwable, message)
}

internal inline fun TaggedLogger?.logW(throwable: Throwable, message: () -> String) {
    logConditionally(Level.Warn, throwable, message)
}

internal inline fun TaggedLogger?.logE(throwable: Throwable, message: () -> String) {
    logConditionally(Level.Error, throwable, message)
}
