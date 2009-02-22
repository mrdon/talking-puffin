package com.davebsoft.sctw.ui

import _root_.com.davebsoft.sctw.util.PopupListener
import _root_.scala.xml.{NodeSeq, Node}
import java.awt.event.{ActionListener, ActionEvent}
import java.util.Comparator
import javax.swing._
import javax.swing.table.{DefaultTableCellRenderer, TableCellRenderer}
import scala.swing._
import filter.TagsRepository

/**
 * Displays friend and public statuses
 */
class StatusPane(statusTableModel: StatusTableModel) extends GridBagPanel {
  var table: JTable = null
  var unmuteButton: Button = null
  
  add(new ScrollPane {
    table = new JTable(statusTableModel)
    table.setAutoCreateRowSorter(true)
    val colModel = table.getColumnModel
    colModel.getColumn(0).setPreferredWidth(60)
    colModel.getColumn(0).setMaxWidth(100)
    colModel.getColumn(0).setCellRenderer(new AgeCellRenderer);
    colModel.getColumn(1).setPreferredWidth(100)
    colModel.getColumn(1).setMaxWidth(200)
    colModel.getColumn(1).setCellRenderer(new NameCellRenderer);
    colModel.getColumn(2).setPreferredWidth(600)
    
    // TODO convert this to scala.swing way
    table.addMouseListener(new PopupListener(table, getPopupMenu));
    peer.setViewportView(table)
  }, new Constraints{
    gridx = 0; gridy = 0; fill = GridBagPanel.Fill.Both; weightx = 1; weighty = 1; 
  })
  
  add(new FlowPanel {
    contents += new Label("Refresh (secs)")
    val comboBox = new ComboBox(List.range(0, 50, 10) ::: List.range(60, 600, 60))
    var defaultRefresh = 120
    comboBox.peer.setSelectedItem(defaultRefresh)
    statusTableModel.setUpdateFrequency(defaultRefresh)
    comboBox.peer.addActionListener(new ActionListener(){
      def actionPerformed(e: ActionEvent) = {  // Couldn’t get to work with reactions
        statusTableModel.setUpdateFrequency(comboBox.selection.item)
      }
    });
    contents += comboBox
    
    val lastSetButton = new Button("Last 200") {
      tooltip = "Loads the last 200 of your “following” tweets"
    }
    lastSetButton.peer.addActionListener(new ActionListener() {
      def actionPerformed(e: ActionEvent) = {
        statusTableModel.loadLastSet
      }
    })
    contents += lastSetButton
    
    val clearButton = new Button("Clear")
    clearButton.peer.addActionListener(new ActionListener() {
      def actionPerformed(e: ActionEvent) = {
        statusTableModel.clear
      }
    })
    contents += clearButton
    
    unmuteButton = new Button("Unmute All")
    unmuteButton.peer.addActionListener(new ActionListener() {
      def actionPerformed(e: ActionEvent) = {
        statusTableModel.unMuteAll
        unmuteButton.enabled = false
      }
    })
    unmuteButton.enabled = false
    contents += unmuteButton
  }, new Constraints{
    gridx = 0; gridy = 1; fill = GridBagPanel.Fill.Horizontal;
  })

  def getPopupMenu: JPopupMenu = {
    val menu = new JPopupMenu()

    val mi = new JMenuItem("Mute")
    mi.addActionListener(new ActionListener() {
      def actionPerformed(e: ActionEvent) = {
        statusTableModel.muteSelectedUsers(getSelectedModelIndexes)
        unmuteButton.enabled = true
      }
    })
    menu.add(mi)

    val tagAl = new ActionListener() {
      def actionPerformed(e: ActionEvent) = {
        statusTableModel.tagSelectedUsers(getSelectedModelIndexes, e.getActionCommand)
      }
    }
    
    
    val tagMi = new JMenu("Tag Friend With")
    for (tag <- TagsRepository.get) {
      val tagSmi = new JMenuItem(tag)
      tagSmi.addActionListener(tagAl)
      tagMi.add(tagSmi)
    }
    menu.add(tagMi)

    menu
  }

  private def getSelectedModelIndexes: List[Int] = {
    val tableRows = table.getSelectedRows
    var smi = List[Int]()
    for (i <- 0 to (tableRows.length - 1)) {
      smi ::= table.convertRowIndexToModel(tableRows(i))
    }
    smi
  }
}
