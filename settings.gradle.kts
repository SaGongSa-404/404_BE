rootProject.name = "404-be"

include(
    ":apps:api",
    ":apps:worker",
    ":modules:auth",
    ":modules:user",
    ":modules:house",
    ":modules:space",
    ":modules:chore",
    ":modules:adjustment",
    ":modules:notification",
    ":modules:review",
    ":modules:shared",
    ":packages:db",
    ":packages:events",
    ":packages:kakao-client",
    ":packages:push-client",
    ":packages:cache",
    ":packages:logger",
    ":packages:observability",
    ":packages:testing",
)

