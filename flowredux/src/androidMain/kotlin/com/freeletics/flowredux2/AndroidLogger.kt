package com.freeletics.flowredux2

import com.freeletics.flowredux2.Logger.Level
import android.util.Log

/**
 * A logger that use Android's [Log].
 */
public class AndroidLogger(
    override var minLevel: Level,
) : Logger {
    override fun log(tag: String, level: Level, message: String?, throwable: Throwable?) {
        when(level) {
            Level.Verbose -> Log.v(tag, message, throwable)
            Level.Debug -> Log.d(tag, message, throwable)
            Level.Info -> Log.i(tag, message, throwable)
            Level.Warn -> Log.w(tag, message, throwable)
            Level.Error -> Log.e(tag, message, throwable)
        }
        throwable?.printStackTrace()
    }


}
