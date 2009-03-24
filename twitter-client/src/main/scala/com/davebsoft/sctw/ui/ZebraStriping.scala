package com.davebsoft.sctw.ui

import java.awt.{Component, Color}
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.JTable

/**
 * Adds alternating color backgrounds to table cells. 
 * @author Dave Briccetti
 */

object ZebraStriping {
  val VERY_LIGHT_GRAY = new Color(240, 240, 240)
}

trait ZebraStriping extends DefaultTableCellRenderer {
  
  override def getTableCellRendererComponent(table: JTable, value: Any, 
      isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component = {
    val comp = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
    if (! isSelected) setBackground(if (row % 2 == 0) Color.WHITE else ZebraStriping.VERY_LIGHT_GRAY)
    comp
  }
}

 