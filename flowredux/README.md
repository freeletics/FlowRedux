# FlowRedux
This module contains the core heart of FlowRedux.
It uses `Flows` and `Channel` to build a processing pipeline of Actions and a reducer to compute state.a

Usually you don't want to use this low level stuff not directly but instead want to use `FlowReduxStateMachine` from DSL module.