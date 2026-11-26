# UI state contract v1

The client exposes one shared lifecycle enum so every screen can render a predictable state. Week 1 defines the contract; network and business screens consume it from week 2 onward.

| State | Meaning | Week 1 shell behavior |
|---|---|---|
| `LOADING` | A screen or request is being initialized. | Reserved for the first real screen. |
| `EMPTY` | The request succeeded but there is no data to display. | Reserved for the first real screen. |
| `ERROR` | A request or local operation failed. | Reserved for the first real screen. |
| `RECONNECTING` | The client is trying to restore a lost connection. | Reserved for network integration. |
| `CLOSED` | The screen, session or auction view is closed. | Reserved for lifecycle handling. |

The week 1 JavaFX shell does not implement network transitions yet. Later screens must keep these names stable and map server errors/events to an appropriate state.
