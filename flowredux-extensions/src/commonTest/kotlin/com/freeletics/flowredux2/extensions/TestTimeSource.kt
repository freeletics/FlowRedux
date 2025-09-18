package com.freeletics.flowredux2.extensions

import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

class TestTimeSource : TimeSource.WithComparableMarks {
    private var currentTimeMark: ComparableTimeMark = TestTimeMark(0.milliseconds)

    override fun markNow(): ComparableTimeMark {
        return currentTimeMark
    }

    fun advanceTimeBy(duration: Duration) {
        currentTimeMark = currentTimeMark.plus(duration)
    }

    private data class TestTimeMark(
        private val elapsed: Duration,
    ) : ComparableTimeMark {
        override fun elapsedNow(): Duration {
            return elapsed
        }

        override fun plus(duration: Duration): ComparableTimeMark {
            return TestTimeMark(elapsed + duration)
        }

        override fun minus(other: ComparableTimeMark): Duration {
            return elapsed - other.elapsedNow()
        }
    }
}
