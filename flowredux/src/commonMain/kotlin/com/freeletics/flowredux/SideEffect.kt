package com.freeletics.flowredux

import kotlinx.coroutines.flow.Flow

/**
 * It is a function which takes a stream of actions and returns a stream of actions. Actions in, actions out
 * (concept borrowed from redux-observable.js.or - so called epics).
 *
 * @param actions Input action. Every SideEffect should be responsible to handle a single Action
 * (i.e using filter or ofType operator)
 * @param state [GetState] to get the latest state of the state machine
 */
// TODO find better name?
internal typealias SideEffect<S, A> = (actions: Flow<A>, getState: GetState<S>) -> Flow<A>

/**
 * The GetState is basically just a deferred way to get a state of a [reduxStore] at any given point in time.
 * So you have to call this method to get the state.
 */
internal typealias GetState<S> = () -> S
