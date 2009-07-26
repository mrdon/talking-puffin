package org.talkingpuffin.ui
import _root_.scala.swing.event.{ButtonClicked, WindowClosing}
import filter.{FilterSet, TextFilter, TagUsers}
import java.awt.{Dimension, PopupMenu, TrayIcon}
import java.awt.event._
import java.util.concurrent.{Callable, Executors, Executor}
import java.util.prefs.Preferences
import javax.swing.border.{BevelBorder, EmptyBorder}
import javax.swing.{SwingWorker, JPopupMenu, ImageIcon}
import org.apache.log4j.Logger
import org.talkingpuffin.mac.QuitHandler
import scala.swing._
import scala.xml._

import TabbedPane._
import state.PreferencesFactory
import talkingpuffin.util.Loggable
import twitter._
import ui._
import ui.util.FetchRequest


/**
 * The top-level application Swing frame window. There is one per user session.
 */
class TopFrame(username: String, password: String, twitterSession: AuthenticatedSession) 
      extends Frame with Loggable {
  val tagUsers = new TagUsers(username)
  TopFrames.addFrame(this)
  val session = new Session(twitterSession)
  Globals.sessions ::= session
  iconImage = new ImageIcon(getClass.getResource("/TalkingPuffin.png")).getImage
  val sysIcon = createSystemIcon
    
  val tabbedPane = new TabbedPane() {
    preferredSize = new Dimension(900, 600)
  }
  session.windows.tabbedPane = tabbedPane

  val mainToolBar = new MainToolBar
  session.progress = mainToolBar
  val streams = new Streams(twitterSession, session, tagUsers, username, password)
  session.windows.streams = streams
  mainToolBar.init(streams)
    
  title = Main.title + " - " + username
  menuBar = new MainMenuBar(destroy)

  contents = new GridBagPanel {
    val userPic = new Label
    val picFetcher = new PictureFetcher(None, (imageReady: PictureFetcher.ImageReady) => {
      if (imageReady.resource.image.getIconHeight <= Thumbnail.THUMBNAIL_SIZE) {
        userPic.icon = imageReady.resource.image 
      }
    })
    picFetcher.requestItem(new FetchRequest(twitterSession.getUserDetail().profileImageURL, null))
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
      visible_=(false)
    }
  }

  peer.setLocationRelativeTo(null)
  createPeoplePane

  def setFocus = streams.streamInfoList.last.pane.requestFocusForTable
  
  def saveState {
    val highFol = streams.tweetsProvider.getHighestId
    val highMen = streams.mentionsProvider.getHighestId
    info("Saving last seen IDs for " + username + ". Following: " + highFol + ", mentions: " + highMen)
    val prefs = PreferencesFactory.prefsForUser(username)
    if (highFol.isDefined) prefs.put("highestId"       , highFol.get.toString())
    if (highMen.isDefined) prefs.put("highestMentionId", highMen.get.toString())
    tagUsers.save
    streams.streamInfoList.last.pane.saveState // TODO instead save the order of the last status pane changed
  }

  private def destroy {
    Globals.sessions = Globals.sessions remove(s => s == session)
    saveState
    TopFrames.removeFrame(this)
  }
  
  private def createPeoplePane: Unit = {
    mainToolBar.startOperation
    val pool = Executors.newFixedThreadPool(2)
    val friendsFuture = pool.submit(new Callable[List[TwitterUser]] {
      def call = twitterSession.loadAll(twitterSession.getFriends)
    })
    val followersFuture = pool.submit(new Callable[List[TwitterUser]] {
      def call = twitterSession.loadAll(twitterSession.getFollowers)
    })

    new SwingWorker[Tuple2[List[TwitterUser],List[TwitterUser]], Object] {
      def doInBackground = (friendsFuture.get, followersFuture.get)

      override def done = {
        val (friends, followers) = get 
              
        streams.usersTableModel.friends = friends
        streams.usersTableModel.followers = followers
        streams.usersTableModel.usersChanged
 
        streams setFollowerIds (followers map (u => u.id.toString()))
              
        val paneTitle = "People (" + friends.length + ", " + followers.length + ")"
        val pane = new PeoplePane(session, streams.usersTableModel, friends, followers)
        tabbedPane.pages += new TabbedPane.Page(paneTitle, pane)
        mainToolBar.stopOperation
      }
    }.execute
  }

  private def createSystemIcon {
    if (java.awt.SystemTray.isSupported) {
      val systray = java.awt.SystemTray.getSystemTray
      val popup = new PopupMenu
      val exit = new java.awt.MenuItem("Exit")
      exit.addActionListener(new ActionListener {
        def actionPerformed(e: ActionEvent) {
          destroy
        }
      })
      popup.add(exit)

      val trayIcon = new TrayIcon(iconImage, "Talking Puffin", popup);
      trayIcon.addMouseListener(new MouseAdapter {
        override def mousePressed(e:MouseEvent) {
          if (e.getButton == MouseEvent.BUTTON1)
            visible_=(true)
        }
      })
      trayIcon.setImageAutoSize(true)
      systray.add(trayIcon)

      return trayIcon
    }
    return Nil
  }



}
  
