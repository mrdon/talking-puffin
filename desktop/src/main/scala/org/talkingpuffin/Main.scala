package org.talkingpuffin

import javax.swing.{ImageIcon, UIManager, JFrame}
import org.apache.log4j.Logger
import scala.swing._
import twitter._
import ui._
/**
 * TalkingPuffin main object
 */
object Main {
  val title = "TalkingPuffin" 
  private var username: String = ""
  private var password: String = ""
  private var user: TwitterSession = _
  
  def main(args: Array[String]): Unit = {
    val props = System.getProperties
    props setProperty("apple.laf.useScreenMenuBar", "true")
    props setProperty("com.apple.mrj.application.apple.menu.about.name", Main.title)
    UIManager setLookAndFeel UIManager.getSystemLookAndFeelClassName
    JFrame setDefaultLookAndFeelDecorated true

    launchSession
  }
  
  def launchSession {
    def startUp(username: String, password: String, user: AuthenticatedSession) {
      this.username = username
      this.password = password
      this.user = user

      new TopFrame(username, password, user) {
        pack
        visible = true
        setFocus
      }
    }

    new LoginDialog(TopFrames.exitIfNoFrames, startUp).display
  }
}

class Session(val twitterSession:AuthenticatedSession) {
  val windows = new Windows
  val status = new Label(" ")
  var progress: LongOpListener = null
}

object Globals {
  var sessions: List[Session] = Nil
}

