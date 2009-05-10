package org.talkingpuffin

import _root_.scala.swing.event.{ButtonClicked, WindowClosing}
import filter.{FilterSet, TextFilter, TagUsers}
import java.awt.event.{ActionEvent, ActionListener, KeyEvent}
import java.awt.{Toolkit, Dimension, BorderLayout, Insets}
import java.util.prefs.Preferences
import javax.swing.border.{BevelBorder, EmptyBorder}
import javax.swing.{JToolBar, KeyStroke, ImageIcon, UIManager, JFrame}
import org.apache.log4j.Logger
import org.talkingpuffin.mac.QuitHandler
import scala.swing._
import scala.xml._

import TabbedPane._
import state.PreferencesFactory
import twitter._
import ui._
import ui.util.FetchRequest

/**
 * TalkingPuffin main object
 *
 * @author Dave Briccetti
 * @author Mark McBride
 */
object Main {
  val log = Logger getLogger "Main"
  val title = "TalkingPuffin" 
  private var user: Node = _
  private var username: String = ""
  private var password: String = ""
  
  object TopFrame {
    var frames = List[TopFrame]()

    QuitHandler register TopFrame.closeAll

    def addFrame(f: TopFrame){
      frames = f :: frames
      log debug "New frame added. Number of frames is " + frames.size + "."
    }

    def removeFrame(f: TopFrame){
      frames = frames.remove {f == _}
      log debug "Frame removed. Number of frames is " + frames.size + "."
      if(frames.size == 0){
          log debug "No more frames active. Exiting."
          // it's kinda ugly to put the exit logic here, but not sure where
          // else to put it.'
          System.exit(0)
      }
    }

    def numFrames = frames.size

    def closeAll: Unit = closeAll(frames)

    def closeAll(frames: List[TopFrame]): Unit = frames match {
      case frame :: rest => {
        frame.dispose
        frame.saveState
        TopFrame removeFrame frame
        closeAll(rest)
      }
      case Nil =>
    }
  }
  
  /**
   * The Swing frame.

   * @author Dave Briccetti
   */
  class TopFrame(username: String) extends Frame{

    val tagUsers = new TagUsers(username)
    TopFrame.addFrame(this)
    val session = new Session
    Globals.sessions ::= session
    iconImage = new ImageIcon(getClass.getResource("/TalkingPuffin.png")).getImage
    
    val tabbedPane = new TabbedPane() {
      preferredSize = new Dimension(900, 600)
    }
    session.windows.tabbedPane = tabbedPane

    val streams = new Streams(session, tagUsers, username, password)
    session.windows.streams = streams
    val mainToolBar = new MainToolBar(streams)
    
    title = Main.title + " - " + username
    
    menuBar = new MenuBar {
      contents += new Menu("Session") {
        contents += new MenuItem(new Action("New...") {
          accelerator = Some(KeyStroke.getKeyStroke(KeyEvent.VK_N, 
            Toolkit.getDefaultToolkit.getMenuShortcutKeyMask))
          def apply = launchSession 
        })
      }
      contents += new Menu("Options") {
        object ItemFactory {
          def apply(title: String, tooltip: String, checked: Boolean, 
              mutator: (Boolean) => Unit): MenuItem = {
            val item = new CheckMenuItem(title) {this.tooltip = tooltip; selected = checked}
            listenTo(item)
            reactions += {
              case r: ButtonClicked => if (r.source == item) mutator(item.selected)
              case _ =>
            }
            item
          }
        }
        contents += ItemFactory("Use animations", "Enables simple, useful animations", 
          Globals.options.useAnimations, Globals.options.useAnimations_=_)
        contents += ItemFactory("Look up locations", "Enables lookup of locations from latitude and longitude", 
          Globals.options.lookUpLocations, Globals.options.lookUpLocations_=_)
        contents += ItemFactory("Expand URLs", "Enables fetching original URL from shortened form", 
          Globals.options.expandUrls, Globals.options.expandUrls_=_)
      }
    }

    contents = new GridBagPanel {
      val userPic = new Label
      val picFetcher = new PictureFetcher(None, (imageReady: PictureFetcher.ImageReady) => {
        if (imageReady.resource.image.getIconHeight <= Thumbnail.THUMBNAIL_SIZE) {
          userPic.icon = imageReady.resource.image 
        }
      })
      picFetcher.requestItem(new FetchRequest((user \ "profile_image_url").text, null))
      add(userPic, new Constraints { grid = (0,0); gridheight=2})
      add(session.status, new Constraints {
        grid = (1,0); anchor=GridBagPanel.Anchor.West; fill = GridBagPanel.Fill.Horizontal; weightx = 1;  
        })
      peer.add(mainToolBar, new Constraints {grid = (1,1); anchor=GridBagPanel.Anchor.West}.peer)
      add(tabbedPane, new Constraints {
        grid = (0,2); fill = GridBagPanel.Fill.Both; weightx = 1; weighty = 1; gridwidth=2})
    }

    reactions += {
      case WindowClosing(_) => {
        Globals.sessions = Globals.sessions remove(s => s == session) // TODO is this best way?
        saveState
        TopFrame.removeFrame(this)
      }
    }

    peer.setLocationRelativeTo(null)

    SwingInvoke.execSwingWorker({
      (new FriendsDataProvider(username, password).getUsers,
        new FollowersDataProvider(username, password).getUsers)
      }, 
      { (result: Tuple2[List[Node],List[Node]]) =>
      val (following, followers) = result 
              
      streams.usersTableModel.friends = following
      streams.usersTableModel.followers = followers
      streams.usersTableModel.usersChanged
 
      streams setFollowerIds (followers map (u => (u \ "id").text))
              
      val paneTitle = "People (" + following.length + ", " + followers.length + ")"
      val pane = new PeoplePane(session, streams.apiHandlers, streams.usersTableModel, following, followers)
      tabbedPane.pages += new TabbedPane.Page(paneTitle, pane)
    })

    private def saveState {
      val highFol = streams.tweetsProvider.getHighestId
      val highMen = streams.mentionsProvider.getHighestId
      log info("Saving last seen IDs for " + username + ". Following: " + highFol + ", mentions: " + highMen)
      val prefs = PreferencesFactory.prefsForUser(username)
      if (highFol != null) prefs.put("highestId", highFol)
      if (highMen != null) prefs.put("highestMentionId", highMen)
      tagUsers.save
    }
  }
  
  def main(args: Array[String]): Unit = {
    val props = System.getProperties
    props setProperty("apple.laf.useScreenMenuBar", "true")
    props setProperty("com.apple.mrj.application.apple.menu.about.name", Main.title)
    UIManager setLookAndFeel UIManager.getSystemLookAndFeelClassName
    JFrame setDefaultLookAndFeelDecorated true

    launchSession
  }
  
  def launchSession {
    def startUp(username: String, pwd: String, user: Node) {
      this.user = user
      this.username = username
      password = pwd

      new TopFrame(username) {
        pack
        visible = true
      }
    }

    new LoginDialog(new twitter.AuthenticationProvider, System.exit(1), startUp).display
  }
}

class ApiHandlers(val sender: Sender, val follower: Follower)

class Session {
  val windows = new Windows
  val status = new Label(" ")
}

object Globals {
  var sessions: List[Session] = Nil
  class Options(var useAnimations: Boolean, var lookUpLocations: Boolean, var expandUrls: Boolean)
  val options = new Options(false, true, false)
}