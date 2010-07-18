/*
 *  NuagesFrame.scala
 *  (Wolkenpumpe)
 *
 *  Copyright (c) 2008-2010 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either
 *  version 2, june 1991 of the License, or (at your option) any later version.
 *
 *  This software is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *  General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public
 *  License (gpl.txt) along with this software; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 *
 *
 *  Changelog:
 */

package de.sciss.nuages

import javax.swing._
import de.sciss.synth.{Server, Model}
import java.awt.geom.Point2D
import collection.mutable.ListBuffer
import de.sciss.synth._
import de.sciss.synth.proc._
import de.sciss.synth.ugen._
import plaf.basic.{BasicSliderUI, BasicPanelUI}
import javax.swing.event.{ChangeListener, ChangeEvent, ListSelectionListener, ListSelectionEvent}
import java.awt.{EventQueue, Component, Container, Color, BorderLayout}

/**
 *    @version 0.12, 18-Jul-10
 */
class NuagesFrame( val config: NuagesConfig )
extends JFrame( "Wolkenpumpe") with ProcDemiurg.Listener {
   frame =>

   val panel               = new NuagesPanel( config.server )
   private val pfPanel     = Box.createVerticalBox
   val transition          = new NuagesTransitionPanel( panel )
   private val models      = Map[ ProcAnatomy, ProcFactoryListModel ](
      ProcGen     -> createProcFactoryView( pfPanel )( panel.genFactory = _ ),
      ProcFilter  -> createProcFactoryView( pfPanel )( panel.filterFactory = _ ),
      ProcDiff    -> createProcFactoryView( pfPanel )( panel.diffFactory = _ ))

   val grpMaster = Group.tail( config.server )
   
   // ---- constructor ----
   {
      val cp = getContentPane
      cp.setBackground( Color.black )
      val ggEastBox     = new JPanel( new BorderLayout() )
      val font          = Wolkenpumpe.condensedFont.deriveFont( 15f ) // WARNING: use float argument
      ProcFactoryCellRenderer.setFont( font )

      val uiPanel       = new BasicPanelUI()
      ggEastBox.setUI( uiPanel )
      ggEastBox.setBackground( Color.black )
      ggEastBox.add( pfPanel, BorderLayout.CENTER )
      ggEastBox.add( transition, BorderLayout.SOUTH )
      cp.add( BorderLayout.EAST, ggEastBox )
      cp.add( BorderLayout.CENTER, panel )

      setDefaultCloseOperation( WindowConstants.DISPOSE_ON_CLOSE )

      config.masterBus foreach { abus =>
         val specSlider       = ParamSpec( 0, 0x10000 )
         val specAmp          = ParamSpec( 0.01, 10.0, ExpWarp )
         val initVal          = specSlider.map( specAmp.unmap( 1.0 )).toInt
         val ggMasterVolume   = new JSlider( SwingConstants.VERTICAL, specSlider.lo.toInt, specSlider.hi.toInt, initVal )
         ggMasterVolume.setUI( new BasicSliderUI( ggMasterVolume ))
         ggMasterVolume.setBackground( Color.black )
         ggMasterVolume.setForeground( Color.white )
         ggMasterVolume.addChangeListener( new ChangeListener {
            def stateChanged( e: ChangeEvent ) {
               val amp = specAmp.map( specSlider.unmap( ggMasterVolume.getValue ))
               grpMaster.set( "amp" -> amp )
            }
         })
         ggEastBox.add( ggMasterVolume, BorderLayout.EAST )
      }

      initAudio

      ProcTxn.atomic { implicit t => ProcDemiurg.addListener( frame )}
   }

   private def initAudio {
      config.masterBus foreach { abus =>
         val df = SynthDef( "nuages-master" ) {
            val bus  = "bus".kr
            val amp  = "amp".kr( 1 )
            val sig  = In.ar( bus, abus.numChannels )
            ReplaceOut.ar( bus, Limiter.ar( sig * amp, (-0.2).dbamp ))
         }
         df.play( grpMaster, List( "bus" -> abus.index ))
      }
   }

   def updated( u: ProcDemiurg.Update ) { defer {
      if( u.factoriesRemoved.nonEmpty ) {
         val byAnatomy = u.factoriesRemoved.groupBy( _.anatomy )
         byAnatomy foreach { tup =>
            val (ana, facts) = tup
            models.get( ana ).foreach( _.remove( facts.toSeq: _* ))
         }
      }
      if( u.factoriesAdded.nonEmpty ) {
         val byAnatomy = u.factoriesAdded.groupBy( _.anatomy ) 
         byAnatomy foreach { tup =>
            val (ana, facts) = tup
            models.get( ana ).foreach( _.add( facts.toSeq: _* ))
         }
      }
   }}

   private def createProcFactoryView( parent: Container )
                                    ( fun: Option[ ProcFactory ] => Unit ) : ProcFactoryListModel = {
      val model   = new ProcFactoryListModel
      val ggList  = new JList( model )
      ggList.setBackground( Color.black )
      ggList.setCellRenderer( ProcFactoryCellRenderer )
      ggList.setFixedCellWidth( 64 )
      ggList.setVisibleRowCount( 10 )
      ggList.setSelectionMode( ListSelectionModel.SINGLE_SELECTION )
      ggList.addListSelectionListener( new ListSelectionListener {
         def valueChanged( e: ListSelectionEvent ) {
            if( e.getValueIsAdjusting() ) return
            val pf0 = ggList.getSelectedValue()
            val pf = if( pf0 != null ) Some( pf0.asInstanceOf[ ProcFactory ]) else None
//            ggPanel.factory = pf
            fun( pf )
         }
      })

      val ggScroll  = new JScrollPane( ggList, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
         ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER )
      parent.add( ggScroll )

      model
   }

   override def dispose {
      ProcTxn.atomic { implicit t => ProcDemiurg.removeListener( frame )}
      panel.dispose
      super.dispose
   }

   private def defer( thunk: => Unit ) {
      EventQueue.invokeLater( new Runnable { def run = thunk })
   }

   private object ProcFactoryCellRenderer extends DefaultListCellRenderer {
      private val colrUnfocused = new Color( 0xC0, 0xC0, 0xC0 )

      override def getListCellRendererComponent( list: JList, value: AnyRef, index: Int,
         isSelected: Boolean, isFocused: Boolean ) : Component = {

         val obj = value match {
            case pf: ProcFactory => pf.name
            case x => x
         }
//         super.getListCellRendererComponent( list, obj, index, isSelected, isFocused )
         setText( obj.toString )
         setBackground( if( isSelected ) { if( isFocused ) Color.white else colrUnfocused } else Color.black )
         setForeground( if( isSelected ) Color.black else Color.white )
         this
      }
   }

   private class ProcFactoryListModel extends AbstractListModel with Ordering[ ProcFactory ] {
      model =>

      private var coll = Vector.empty[ ProcFactory ]

      def remove( pfs: ProcFactory * ) {
         val indices = pfs.map( Util.binarySearch( coll, _ )( model )).filter( _ >= 0 )
         coll = coll.diff( pfs )
         val index0 = indices.min
         val index1 = indices.max
         removed( index0, index1 ) // WARNING: IllegalAccessError with fireIntervalRemoved
      }

      def add( pfs: ProcFactory* ) {
         var index0 = Int.MaxValue
         var index1 = Int.MinValue
         pfs.foreach( pf => {
            val idx  = Util.binarySearch( coll, pf )( model )
            val idx0 = if( idx < 0) (-idx - 1) else idx
            coll     = coll.patch( idx0, Vector( pf ), 0 )
            // goddamnit
            if( idx0 <= index1 ) index1 += 1
            index0   = math.min( index0, idx0 )
            index1   = math.max( index1, idx0 )
         })
         // WARNING: IllegalAccessError with fireIntervalAdded
         if( index0 <= index1 ) added( index0, index1 )
      }

      private def removed( index0: Int, index1: Int ) {
         fireIntervalRemoved( model, index0, index1 )
      }

      private def added( index0: Int, index1: Int ) {
         fireIntervalAdded( model, index0, index1 )
      }

      // Ordering
      def compare( a: ProcFactory, b: ProcFactory ) = a.name.toUpperCase.compare( b.name.toUpperCase )

      // AbstractListModel
      def getSize : Int = coll.size
      def getElementAt( idx: Int ) : ProcFactory = coll( idx )
   }
}