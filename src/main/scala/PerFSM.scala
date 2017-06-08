import akka.actor.{ActorLogging, ActorRef, ActorSystem, ExtendedActorSystem, Props}
import akka.persistence.fsm.PersistentFSM.FSMState

import scala.reflect.ClassTag
import SimpleTransitionFSM._
import akka.persistence.fsm.PersistentFSM
import akka.persistence.journal.{EventAdapter, EventSeq, Tagged}
import com.typesafe.config.ConfigFactory

object PerFSM extends App {
  val sys = ActorSystem("PerFSM", ConfigFactory.parseString(
    """
      |akka.loglevel = DEBUG
      |
      |akka.persistence.journal.leveldb.native = off
      |akka.persistence.journal.plugin = "jdbc-journal"
      |akka.persistence.max-concurrent-recoveries = 50
      |
      |slick {
      |  driver = "slick.driver.PostgresDriver$"
      |  db {
      |    url = "jdbc:postgresql://localhost:5432/postgres"
      |    user = "postgres"
      |    password = "mysecretpassword"
      |    driver = "org.postgresql.Driver"
      |
      |    // hikariCP
      |    numThreads = 1 // number of cores
      |    maxConnections = 1 // 2 * numThreads + 1 (if running on an SSD)
      |    minConnections = 1  // same as numThreads
      |
      |    connectionTestQuery = SELECT 1 // postgres doesnt support connection timeout
      |  }
      |}
      |
      |jdbc-journal {
      |  tables {
      |    journal {
      |      tableName = "journal"
      |      schemaName = "akka_persistence_jdbc"
      |    }
      |
      |    deletedTo {
      |      tableName = "deleted_to"
      |      schemaName = "akka_persistence_jdbc"
      |    }
      |  }
      |
      |  slick = ${slick}
      |
      |  event-adapters {
      |    tagger = "SimpleTransitionFSM$MyTaggingEventAdapter"
      |  }
      |
      |  event-adapter-bindings {
      |    "akka.persistence.fsm.PersistentFSM$StateChangeEvent" = tagger
      |  }
      |}
      |
    """.stripMargin).resolve.withFallback(ConfigFactory.load))

  val fsm = sys.actorOf(SimpleTransitionFSM.props("SimpleTransitionFSM2", ActorRef.noSender))

  args.head match {
    case "msg" =>
      fsm ! "stay"
      fsm ! "shop"
    case "log" =>
      println("will log")
      fsm ! "log"
  }

  scala.io.StdIn.readLine()

  sys.terminate()
}

class SimpleTransitionFSM(_persistenceId: String, reportActor: ActorRef)(implicit val domainEventClassTag: ClassTag[DomainEvent]) extends PersistentFSM[UserState, ShoppingCart, DomainEvent] {
  override val persistenceId = _persistenceId

  startWith(LookingAround, EmptyShoppingCart)

  when(LookingAround) {
    case Event("stay", _) ⇒ stay
    case Event("shop", _) => goto(Shopping)
    case Event("log", _) =>
      log.info("Currently LookingAround")
      stay
    case Event(e, _)      ⇒ goto(LookingAround)
  }

  when(Shopping) {
    case Event("log", _) =>
      log.info("Currently Shopping")
      stay
  }

  onTransition {
    case (from, to) ⇒ //reportActor ! s"$from -> $to"
  }

  override def applyEvent(domainEvent: DomainEvent, currentData: ShoppingCart): ShoppingCart =
    currentData
}

object SimpleTransitionFSM {

  // --------- customer-states

  sealed trait UserState extends FSMState
  case object LookingAround extends UserState {
    override def identifier: String = "Looking Around"
  }
  case object Shopping extends UserState {
    override def identifier: String = "Shopping"
  }
  case object Inactive extends UserState {
    override def identifier: String = "Inactive"
  }
  case object Paid extends UserState {
    override def identifier: String = "Paid"
  }

  // ----------- customer-states-data

  case class Item(id: String, name: String, price: Float)

  sealed trait ShoppingCart {
    def addItem(item: Item): ShoppingCart
    def empty(): ShoppingCart
  }
  case object EmptyShoppingCart extends ShoppingCart {
    def addItem(item: Item) = NonEmptyShoppingCart(item :: Nil)
    def empty() = this
  }
  case class NonEmptyShoppingCart(items: Seq[Item]) extends ShoppingCart {
    def addItem(item: Item) = NonEmptyShoppingCart(items :+ item)
    def empty() = EmptyShoppingCart
  }

  // --------------- customer-commands

  sealed trait Command
  case class AddItem(item: Item) extends Command
  case object Buy extends Command
  case object Leave extends Command
  case object GetCurrentCart extends Command

  // --------------- customer-domain-events

  sealed trait DomainEvent
  case class ItemAdded(item: Item) extends DomainEvent
  case object OrderExecuted extends DomainEvent
  case object OrderDiscarded extends DomainEvent

  def props(persistenceId: String, reportActor: ActorRef) =
    Props(new SimpleTransitionFSM(persistenceId, reportActor))

  class MyTaggingEventAdapter(system: ExtendedActorSystem) extends EventAdapter {
    override def manifest(event: Any): String = ""

    override def fromJournal(event: Any, manifest: String): EventSeq = event match {
      case j => EventSeq.single(j)
    }

    override def toJournal(event: Any): Any = {
      Tagged(event, Set("testtag"))
    }
  }
}
