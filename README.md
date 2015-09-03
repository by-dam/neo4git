# Exploring GIT's social graph

Most people use Git simply as a version control system and there is nothing wrong with it.
In fact it was precisely designed for it.

However I think a git repository holds much more information than the content of its files.

For example by following the git's history graph one can derive relations between files or users.

## What is the Git's social graph

So what do I mean by the `Git's social graph` ?
A git's history as most people know is a set of commits related to one another.

![Commits](https://github.com/by-dam/neo4git/blob/master/docs/git-commits.png)

And a commit is a set of files modified by some user so it actually contains also user and files information.

![Commits, users and files](https://github.com/by-dam/neo4git/blob/master/docs/git-commits-files-users.png)

From it one can derive relation between files: when 2 files are changed in the same commit there is probably some sort of relation between them. And the more commits they appear together the stronger the relations. Once we know which files are related together we can almost draw a class diagram without knowing anything about the content of the files themselves.

Similarly we can derive relations between users. How? When a user commits a file that has already been commited by another user, we can infer a relationship between these 2 users as the second user sees the work of the first users. Using the number of commits we can infer the strength of such relations.

![Inferred relations](https://github.com/by-dam/neo4git/blob/master/docs/git-inferred-relations.png)

## Usage

### Prerequisites

You need Scala, SBT and Neo4j (and of course a JDK) installed on your machine.

### Loading a Git's repo into Neo4j

From the project repo starts the sbt console:
`# sbt console`

Then from the console run the following code:
```
import n4g.neo._
import n4g.stream.Git2NeoStream._

// create a neo4j database
val cnx = Neo4JConnection("./myrepo.db")
cnx.init

// load a git repo's history and store it into neo4j DB
git("/path/to/a/git/repo").via(neo4j(cnx)).to(stdout).run

cnx.shutdown
```
### Querying the social graph

Once the Neo4j database is created we need to point neo4j to our database and restart it.
For this edit neo4j configuration file `neo4j-server.properties` and change the property `org.neo4j.server.database.location` to point to our newly generated db.

Then don't forget to (re)start neo4j.

Once neo4j is ready we can simply use neoj-shell or the neo4j browser to query our graph.

### Visualizing the social graph

Neo4j browser provides some d3 visualization. However it works fine only to visualize a few nodes.
In order to visualize the whole graph I turn to Gephi.

Once gephi is installed we need to export our neo4j data into a Gephi compatible format (e.g. graphml).

In order to do this I use the neo4j-shell-tools which allows to run the following commands from the neo4j-shell:

```
export-graphml -t -o ./users.graphml MATCH (a:User)-[r:COMMUNICATE]-(b:User) RETURN a,r
export-graphml -t -o ./files.graphml MATCH (a:File)-[r:RELATED]-(b:File) WHERE a.lines > 0 AND b.lines > 0 RETURN a,r
```
## Social graph insights

### Finding bugs

Where to bugs hide? Well, probably in big enough files which have changed recently. And it is easy to query Neo4j for such files.

```
MATCH (f: File)-[]-(c: Commit)
WHERE c.date > '2015-08-01'
AND f.lines > 1000
```

### File recommendations

Once the relations are stored into neo4j we can query it easily.
For instance for a given a file we can find out which files are most likely going to be part of the same commit.

We can then imaging improving git's commit message with "amazon" like recommendations:
`Users who have commited this file have also commited the following files: ...`

### Code structure

By visualizing files relations we can get a sense of the project structure. Is it divided into independant modules or is it a monolithic application ?

![File structure](https://github.com/by-dam/neo4git/blob/master/docs/gephi-files.png)

### People organisation

By visualizing relations between users we can sometimes infer the organisation structure and see if there is a flat structure where everyone communicates with everyone or if there is a more hierarchical structure where people are divided into teams with a limited communication channel between them.

![File structure](https://github.com/by-dam/neo4git/blob/master/docs/gephi-users.png)
