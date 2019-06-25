# Akka RabbitMQ client [![Build Status](https://secure.travis-ci.org/pjfanning/akka-rabbitmq.svg)](http://travis-ci.org/pjfanning/akka-rabbitmq)

**Please use https://github.com/NewMotion/akka-rabbitmq 5.1.1 or above to get scala 2.13 support**

This small library allows you use [RabbitMQ client](http://www.rabbitmq.com/java-client.html) via [Akka Actors](http://akka.io).
The main idea implemented in library is to survive losing connection with RabbitMQ server

It gives you two actors `ConnectionActor` and `ChannelActor`

### ConnectionActor
* handles connection failures and notifies children
* keep trying to reconnect if connection lost
* provides children with new channels when needed

### ChannelActor
* may store messages in memory if channel lost
* send stored messages as soon as new channel received
* retrieve new channel if current is broken

Please note that while this library transparently reconnects when a connection fails, it **cannot guarantee** that no
messages will be lost. If you want to make sure every message is delivered, you have to use acknowledgements
and confirms. This is documented
[in the RabbitMQ Reliability Guide](https://www.rabbitmq.com/reliability.html#connection-failures). An example program
using confirms can be found in this project under [ConfirmsExample.scala](https://github.com/NewMotion/akka-rabbitmq/blob/master/src/test/scala/akka/rabbitmq/examples/ConfirmsExample.scala).

## Setup

### Sbt
``` scala
libraryDependencies += "com.github.pjfanning" %% "akka-rabbitmq" % "5.1.0"
```

### Maven
```xml
<dependency>
    <groupId>com.github.pjfanning</groupId>
    <artifactId>akka-rabbitmq_{2.11/2.12}</artifactId>
    <version>5.1.0</version>
</dependency>
```

## Tutorial in comparisons
Before start, you need to add import statement

```scala
    import com.newmotion.akka.rabbitmq._
```

### Create connection

Default approach:
```scala
    val factory = new ConnectionFactory()
    val connection: Connection = factory.newConnection()
```

Actor style:
```scala
    val factory = new ConnectionFactory()
    val connectionActor: ActorRef = system.actorOf(ConnectionActor.props(factory))
```

Let's name it:
```scala
    system.actorOf(ConnectionActor.props(factory), "my-connection")
```

How often will it reconnect?
```scala
    import concurrent.duration._
    system.actorOf(ConnectionActor.props(factory, reconnectionDelay = 10.seconds), "my-connection")
```

### Create channel

That's plain option:
```scala
    val channel: Channel = connection.createChannel()
```

But we can do better. Asynchronously:
```scala
    connectionActor ! CreateChannel(ChannelActor.props())
```

Synchronously:
```scala
    val channelActor: ActorRef = connectionActor.createChannel(ChannelActor.props())
```

Maybe give it a name:
```scala
    connectionActor.createChannel(ChannelActor.props(), Some("my-channel"))
```

What's about custom actor:
```scala
    connectionActor.createChannel(Props(new Actor {
      def receive = {
        case channel: Channel =>
      }
    }))
```

### Setup channel
```scala
    channel.queueDeclare("queue_name", false, false, false, null)
```

Actor style:
```scala
    // this function will be called each time new channel received
    def setupChannel(channel: Channel, self: ActorRef) {
      channel.queueDeclare("queue_name", false, false, false, null)
    }
    val channelActor: ActorRef = connectionActor.createChannel(ChannelActor.props(setupChannel))
```

### Use channel
```scala
    channel.basicPublish("", "queue_name", null, "Hello world".getBytes)
```

Using our `channelActor`:
```scala
    def publish(channel: Channel) {
      channel.basicPublish("", "queue_name", null, "Hello world".getBytes)
    }
    channelActor ! ChannelMessage(publish)
```

But I don't want to lose messages when connection is lost:
```scala
    channelActor ! ChannelMessage(publish, dropIfNoChannel = false)
```

### Close channel
```scala
    channel.close()
```
VS
```scala
    system stop channelActor
```

### Close connection

```scala
    connection.close()
```
VS
```scala
    system stop connectionActor // will close all channels associated with this connection
```

You can shutdown `ActorSystem`, this will close all connections as well as channels:
```scala
    system.shutdown()
```

## Examples:

### Publish/Subscribe

Here is [RabbitMQ Publish/Subscribe](http://www.rabbitmq.com/tutorials/tutorial-three-java.html) in actors style

```scala
object PublishSubscribe extends App {
  implicit val system = ActorSystem()
  val factory = new ConnectionFactory()
  val connection = system.actorOf(ConnectionActor.props(factory), "akka-rabbitmq")
  val exchange = "amq.fanout"

  def setupPublisher(channel: Channel, self: ActorRef) {
    val queue = channel.queueDeclare().getQueue
    channel.queueBind(queue, exchange, "")
  }
  connection ! CreateChannel(ChannelActor.props(setupPublisher), Some("publisher"))

  def setupSubscriber(channel: Channel, self: ActorRef) {
    val queue = channel.queueDeclare().getQueue
    channel.queueBind(queue, exchange, "")
    val consumer = new DefaultConsumer(channel) {
      override def handleDelivery(consumerTag: String, envelope: Envelope, properties: BasicProperties, body: Array[Byte]) {
        println("received: " + fromBytes(body))
      }
    }
    channel.basicConsume(queue, true, consumer)
  }
  connection ! CreateChannel(ChannelActor.props(setupSubscriber), Some("subscriber"))

  Future {
    def loop(n: Long) {
      val publisher = system.actorSelection("/user/akka-rabbitmq/publisher")

      def publish(channel: Channel) {
        channel.basicPublish(exchange, "", null, toBytes(n))
      }
      publisher ! ChannelMessage(publish, dropIfNoChannel = false)

      Thread.sleep(1000)
      loop(n + 1)
    }
    loop(0)
  }

  def fromBytes(x: Array[Byte]) = new String(x, "UTF-8")
  def toBytes(x: Long) = x.toString.getBytes("UTF-8")
}
```

## Changelog
### 5.1.0

 * Upgrade to AMQP Client 5.7.1
 * Support Scala 2.13.0

## Other Libraries

Akka-RabbitMQ is a low-level library, and leaves it to the coder to manually wire consumers, serialize messages, etc. If you'd like a higher-level abstraction library, look at [Op-Rabbit](https://github.com/pjfanning/op-rabbit.git) (which uses this library).
