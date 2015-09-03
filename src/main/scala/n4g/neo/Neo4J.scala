package n4g.neo

import n4g.Model.{Id, File, User, Commit}
import org.neo4j.graphdb._
import org.neo4j.graphdb.factory.GraphDatabaseFactory
import org.neo4j.graphdb.Direction._
import scala.annotation.tailrec
import scala.collection.JavaConversions._
import scala.util.{Failure, Success, Try}


object Neo4JConnection {

  def apply(path: String): Neo4JConnection = Neo4JConnection(
    new GraphDatabaseFactory()
      .newEmbeddedDatabaseBuilder(path)
      .newGraphDatabase()
    )

}

case class Neo4JConnection(graphDb: GraphDatabaseService) {

  private case class Entity(label: String, params: Map[String, AnyRef])

  private object Entity {

    // concat repo and path into a unique key
    def apply(f: File): Entity = Entity("File", Map(
      "fullPath" -> s"${f.repo}/${f.path}",
      "repo" -> f.repo, "path" -> f.path,
      "lines" -> f.lines.asInstanceOf[Integer],
      "name" -> f.name,
      "ext" -> f.ext,
      "purpose" -> f.purpose))
    def apply(u: User): Entity = Entity("User", Map(
      "email" -> u.email,
      "name" -> u.name))
    def apply(c: Commit): Entity = Entity("Commit", Map(
      "id" -> c.id,
      "time" -> c.time.asInstanceOf[Integer],
      "msg" -> c.msg))
    def apply(id: Id): Entity = Entity("Commit", Map(
      "id" -> id))

  }

  case class RelationType(label: String) extends RelationshipType {
    def name = label
  }

  val PUSH = RelationType("PUSH")
  val PARENT = RelationType("PARENT")
  val IMPACT = RelationType("IMPACT")
  val RELATED = RelationType("RELATED")
  val CHANGE = RelationType("CHANGE")
  val COMMUNICATE = RelationType("COMMUNICATE")

  /**
   * Initialize the DB and create indexes
   */
  def init(): Unit = {
    val indexes = Map(
      "User" -> List("email"),
      "File" -> List("fullPath"),
      "Commit" -> List("id")
    )
    withTx { tx =>
      val schema = graphDb.schema
      indexes.foreach { case (label, fields) =>
        val index = schema.constraintFor(DynamicLabel.label(label))
        fields.foreach(index.assertPropertyIsUnique(_).create)
      }
    }
  }

  def shutdown(): Unit = graphDb.shutdown

  private def withTx[T](query: Transaction => T): Try[T] = {
    val tx = graphDb.beginTx
    val res = Try(query(tx))
    res match {
      case Success(_) => tx.success()
      case Failure(e) =>
        tx.failure()
        e.printStackTrace
    }
    tx.close()
    res
  }

  /**
   * Create a node from an entity and sets the properties
   */
  private def init(entity: Entity)(node: Entity => Node): Node = {
    val n = node(entity)
    entity.params.foreach {
      case (k, v) => n.setProperty(k, v)
    }
    n
  }

  /**
   * Get or create a unique node
   */
  private def unique(entity: Entity): Node = find(entity) getOrElse {
      val label = entity.label
      val props = entity.params.keys.map(k => s"$k: {$k}").mkString(",")
      val q = s"MERGE (n:$label {$props}) RETURN n"
      val p = mapAsJavaMap(entity.params)
      val node: ResourceIterator[Node] = graphDb.execute(q, p).columnAs("n")
      node.next
    }

  private def find(entity: Entity): Option[Node] = {
    val label = DynamicLabel.label(entity.label)
    val (k, v) = entity.params.head
    Option(graphDb.findNode(label, k, v))
  }

  /**
   * Find a relation in the given relations collection or create a new one if not found
   */
  private def relationship(start: Node, end:Node, label: RelationType, relations: Iterable[Relationship]): Relationship =
    relations
      .find(_.getOtherNode(start).getId == end.getId)
      .getOrElse(start.createRelationshipTo(end, label))

  /**
   * Find or create a relation between 2 nodes
   */
  private def relation(start: Node, end: Node, label: RelationType, dir: Direction = BOTH): Relationship =
    relationship(start, end, label, start.getRelationships(label, dir))

  private def inc(k: String, e: PropertyContainer): Unit = {
    val v = Try(e.getProperty(k)).map(_.asInstanceOf[Integer].toInt).getOrElse(0)
    e.setProperty(k, (v + 1).asInstanceOf[Integer])
  }

  /**
   * Store a commit in DB (store all nodes and relations)
   */
  def store(commit: Commit): Option[Commit] = withTx { tx =>
    // we need to init the commit node as it may have been created
    // by another commit that references this commit id
    // In this case the commit properties were not set
    // (only the commit id was known)
    val c = init(Entity(commit))(unique)
    val u = unique(Entity(commit.user))
    inc("commits", u)
    u.createRelationshipTo(c, PUSH)

    commit.parents.foreach { id =>
      val p = unique(Entity(id))
      c.createRelationshipTo(p, PARENT)
    }

    val files = commit.files.map(f => unique(Entity(f)))
    files.foreach { f =>
      c.createRelationshipTo(f, IMPACT)
      inc("commits", f)
      inc("force", relation(u, f, CHANGE, OUTGOING))

      val peers = u.getRelationships(COMMUNICATE, BOTH)
      f.getRelationships(CHANGE, INCOMING).filterNot(_.getStartNode.getId == u.getId).foreach { ch =>
        inc("force", relationship(u, ch.getStartNode, COMMUNICATE, peers))
      }
    }
    linkAll(RELATED, files)

    commit
  }.toOption

  /**
   * creates relations between all nodes in the list
   * @param nodes
   */
  @tailrec
  private def linkAll(relation: RelationType, nodes: List[Node]): Unit = nodes match {
    case f :: fs =>
      val rs = f.getRelationships(relation, BOTH)
      fs.foreach { g =>
        inc("force", relationship(f, g, relation, rs))
      }
      linkAll(relation, fs)
    case _ =>
  }

}
