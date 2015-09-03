package n4g.stream

import akka.actor.ActorSystem
import akka.stream.ActorFlowMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import n4g.Model._
import n4g.git.Gitlog
import n4g.neo.Neo4JConnection

trait GitStream {

  implicit val system = ActorSystem("Sys")
  implicit val materializer = ActorFlowMaterializer()

  def git(path: String, days: Option[Int] = None): Source[Commit, Unit] = Source(() => Gitlog.log(path, days))

  val stdout = Sink.foreach(println)

}

/**
 * usage:
 * val cnx = Neo4JConnection("~/my/neo4j.db")
 * cnx.init
 * git("~/my/git/repo").via(neo4j(cnx)).to(stdout).run
 * cnx.shutdown
 */
object Git2NeoStream extends GitStream {

  def neo4j(cnx: Neo4JConnection) = Flow[Commit].map { c =>
    val res = cnx.store(c) match {
      case Some(c) => "OK"
      case _ => "ERROR"
    }
    s"$res -> [${c.id}] ${c.user.email} - ${c.msg}"
  }

}



