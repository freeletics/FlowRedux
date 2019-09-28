package com.freeletics.flowredux

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow

/**
 * It is a function which takes a stream of actions and returns a stream of actions. Actions in, actions out
 * (concept borrowed from redux-observable.js.or - so called epics).
 *
 * @param actions Input action. Every SideEffect should be responsible to handle a single Action
 * (i.e using filter or ofType operator)
 * @param state [StateAccessor] to get the latest state of the state machine
 */
// @FlowPreview would be nicer because it's viral and let's the user know that this is using an
// experimental API, but can't be used on a typealias. It's ok though because reduxStore has it.
// TODO find better name?
typealias SideEffect<S, A> = (actions: Flow<A>, state: StateAccessor<S>) -> Flow<A>

/**
 * The StateAccessor is basically just a deferred way to get a state of a [reduxStore] at any given point in time.
 * So you have to call this method to get the state.
 */
// TODO find better name
typealias StateAccessor<S> = () -> S
