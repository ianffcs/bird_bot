# My bot

to run add a sys.properties file with the property token as the telegram bot token and run with

```shell
bb -m main 
```

or to not send messages and only update the local bck
```shell
bb -x main/-main --dont-send true
```

Everything is repeatable in every editor.

## Build

Create a AoT jar with the command:

```shell
clojure -M:dev -m bird-bot.build
```

Will generate `target/bird-bot.jar`

Generate a binary from your jar file with

```shell
## still not working
native-image target/bird-bot.jar
```
