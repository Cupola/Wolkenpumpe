import xml._
import sbt.{ FileUtilities => FU, _}

/**
 *    @version 0.12, 02-Aug-10
 */
class WolkenpumpeProject( info: ProjectInfo ) extends DefaultProject( info ) {
   // stupidly, we need to redefine the dependancy here, because
   // for some reason, sbt will otherwise try to look in the maven repo
   val scalaAudioFile      = "de.sciss" %% "scalaaudiofile" % "0.13"
   val scalaOSC            = "de.sciss" %% "scalaosc" % "0.19"
   val scalaCollider       = "de.sciss" %% "scalacollider" % "0.17"
   val prefuse             = "prefuse" % "prefuse" % "beta-20071021" from "http://github.com/downloads/Sciss/ScalaColliderSwing/prefuse-beta-20071021.jar"
   val scalaColliderSwing  = "de.sciss" %% "scalacolliderswing" % "0.17"
   val soundProcesses      = "de.sciss" %% "soundprocesses" % "0.15"
}
