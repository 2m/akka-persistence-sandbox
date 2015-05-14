import akka.actor._
import akka.persistence._
import scala.io.StdIn

/**
 * To run this puppy:
 * `sbt stage`
 * and then:
 */
// java -cp "target/universal/stage/lib/" AtLeastOnceDeliveryEvo
object AtLeastOnceDeliveryEvo extends App {

  class Clerk extends PersistentActor with AtLeastOnceDelivery with ActorLogging {

    override def persistenceId = "clerk-1"

    val buyer = context.actorOf(Props[Buyer])
    var itemsBought = Vector.empty[String]

    def updateState(itemId: String) = itemsBought +:= itemId

    val receiveCommand: Receive = {
      case Clerk.Command.Item(itemId) =>
        persist(Clerk.Event.NewItem(itemId)) { event =>
          log.info(s"New item: $itemId")
          deliver(buyer.path, deliveryId => Buyer.BuyRequest(deliveryId, itemId))
        }
      case Clerk.Command.BuyAck(deliveryId, itemId) =>
        persist(Clerk.Event.BuyAck(deliveryId, itemId)) { event =>
          log.info(s"Item bought: $itemId")
          confirmDelivery(deliveryId)
          updateState(itemId)
        }
      case Clerk.Command.Info => log.info(s"Current state: $itemsBought")
    }

    val receiveRecover: Receive = {
      case Clerk.Event.NewItem(itemId) => deliver(buyer.path, deliveryId => Buyer.BuyRequest(deliveryId, itemId))
      case Clerk.Event.BuyAck(deliveryId, itemId) => {
        updateState(itemId)
        confirmDelivery(deliveryId)
      }
      case RecoveryCompleted => {
        log.info("Recovery completed")
        self ! Clerk.Command.Info
      }
    }

  }

  object Clerk {
    object Command {
      case object Info
      case class Item(itemId: String)
      case class BuyAck(deliveryId: Long, itemId: String)
    }
    object Event {
      case class NewItem(itemId: String)
      case class BuyAck(deliveryId: Long, itemId: String)
    }
  }

  class Buyer extends Actor {

    def receive = {
      case Buyer.BuyRequest(deliveryId, itemId) =>
        if (math.random < 0.75) {
          sender() ! Clerk.Command.BuyAck(deliveryId, itemId)
        }
    }

  }

  object Buyer {
    case class BuyRequest(deliveryId: Long, itemId: String)
  }

  val system = ActorSystem("AtLeastOnceDeliveryEvo")
  val clerk = system.actorOf(Props[Clerk])

  val Item = "item:([a-z]*)".r

  Iterator.continually(StdIn.readLine).takeWhile(_ != "q").foreach {
    case Item(itemId) => clerk ! Clerk.Command.Item(itemId)
    case "info" => clerk ! Clerk.Command.Info
    case _ => println("Unknown input. Type 'q' to quit.")
  }

  system.shutdown()
  system.awaitTermination()
}
