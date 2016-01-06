/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2006-2014 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2014 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.groovy.poller.remote

import java.awt.Color
import java.awt.Dimension
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener

import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.SwingUtilities

import org.opennms.netmgt.config.monitoringLocations.LocationDef
import org.opennms.netmgt.poller.remote.PollerBackEnd
import org.opennms.netmgt.poller.remote.PollerFrontEnd.PollerFrontEndStates
import org.opennms.netmgt.poller.remote.support.ScanReportPollerFrontEnd
import org.opennms.netmgt.poller.remote.support.ScanReportPollerFrontEnd.ScanReportProperties
import org.springframework.beans.factory.InitializingBean
import org.springframework.util.Assert

import com.jgoodies.validation.ValidationResultModel
import com.jgoodies.validation.util.DefaultValidationResultModel

class ScanGui extends AbstractGui implements InitializingBean, PropertyChangeListener {
    def m_metadataFieldNames = ['Customer Account Number', 'Reference ID', 'Customer Name']
    def m_locations = new ArrayList<String>()

    def m_backEnd

    def m_metadataFields = new HashMap<String, JTextField>()
    def m_progressBar

    public ScanGui() {
        super()
    }

    public void setPollerBackEnd(final PollerBackEnd pollerBackEnd) {
        m_backEnd = pollerBackEnd
    }

    @Override
    protected String getHeaderText() {
        return "Network Scanner"
    }

    /**
     * This method is injected by Spring by using a <lookup-method>.
     *
     * @see applicationContext-scan-gui.xml
     */
    protected ScanReportPollerFrontEnd createPollerFrontEnd() {
    }

    public void afterPropertiesSet() {
        Assert.notNull(m_backEnd)
        Collection<LocationDef> monitoringLocations = m_backEnd.getMonitoringLocations()
        for (final LocationDef d : monitoringLocations) {
            m_locations.add(d.getLocationName())
        }
        SwingUtilities.invokeLater( { createAndShowGui() } )
    }

    public String validateFields() {
        for (final String key : m_metadataFieldNames) {
            final String fieldKey = getFieldKey(key);
            final JTextField field = m_metadataFields.get(fieldKey);
            if (field != null) {
                if (field.getText() == null || "".equals(field.getText())) {
                    return key + " is required!"
                }
            }
        }
        return null
    }

    protected String getFieldKey(final String name) {
        if (name == null) {
            return null
        }
        return name.replace(" ", "-").toLowerCase()
    }

    @Override
    public JPanel getMainPanel() {
        def errorLabel

        def updateValidation = {
            def errorMessage = validateFields()
            System.err.println("error message: " + errorMessage)
            if (errorLabel != null) {
                if (errorMessage == null) {
                    errorLabel.setText("")
                    errorLabel.setVisible(false)
                    return false
                } else {
                    errorLabel.setText(errorMessage)
                    errorLabel.setVisible(true)
                    return true
                }
            } else {
                return true
            }
        }


        return swing.panel(background:getBackgroundColor(), opaque:true, constraints:"grow") {
            migLayout(
                    layoutConstraints:"fill" + debugString,
                    columnConstraints:"[right,grow][left,grow]",
                    rowConstraints:"[grow]"
                    )

            panel(constraints:"top", opaque:false) {
                migLayout(
                        layoutConstraints:"fill" + debugString,
                        columnConstraints:"[right,grow][left][left]",
                        rowConstraints:"[grow]"
                        )

                label(text:"Location:", font:getLabelFont())
                def locationCombo = comboBox(items:m_locations, toolTipText:"Choose your location.", foreground:getForegroundColor(), background:getBackgroundColor(), renderer:getRenderer())
                button(text:'Go', font:getLabelFont(), foreground:getBackgroundColor(), background:getDetailColor(), opaque:true, constraints:"wrap", actionPerformed:{
                    if (updateValidation()) {
                        return
                    }

                    m_progressBar.setValue(0)
                    m_progressBar.setStringPainted(true)
                    m_progressBar.setString("0%")
                    m_progressBar.setVisible(true)
                    m_progressBar.updateUI()
                    final ScanReportPollerFrontEnd fe = createPollerFrontEnd()

                    final Map<String,String> metadata = new HashMap<>()
                    for (final Map.Entry<String,JTextField> field : m_metadataFields) {
                        metadata.put(field.getKey(), field.getValue().getText())
                    }
                    fe.setMetadata(metadata)

                    fe.addPropertyChangeListener(this)
                    fe.initialize()
                    fe.register(locationCombo.getSelectedItem())
                })

                m_progressBar = progressBar(borderPainted:false, visible:false, value:0, constraints:"grow, spanx 3, wrap")

                label(text:"Yes/No", font:getHeaderFont(), constraints:"center, spanx 3, spany 2, height 200!, wrap")
            }
            panel(constraints:"top", opaque:false) {
                migLayout(
                        layoutConstraints:"fill" + debugString,
                        columnConstraints:"[right][left grow,fill, 200::]",
                        rowConstraints:""
                        )

                for (def field : m_metadataFieldNames) {
                    final String key = getFieldKey(field)
                    label(text:field, font:getLabelFont(), constraints:"")
                    def textField = textField(toolTipText:"Enter your " + field.toLowerCase() + ".", columns:25, constraints:"wrap", actionPerformed:updateValidation, focusGained:updateValidation, focusLost:updateValidation)
                    m_metadataFields.put(key, textField)
                }

                errorLabel = label(text:"", visible:false, foreground:Color.RED, constraints:"grow, skip 1, wrap")
            }

            def detailsOpen = false
            def pendingResize = true
            def detailsButton
            def detailsParent
            def detailsPanel

            def updateDetails = {
                if (detailsButton == null || detailsParent == null) {
                    return
                }
                //println "Details button is " + (detailsOpen? "":"not ") + "open."

                if (detailsOpen) {
                    detailsButton.setText("Details \u25BC")
                    detailsPanel = panel(opaque:false) {
                        migLayout(
                                layoutConstraints:"fill" + debugString,
                                columnConstraints:"[center grow]",
                                rowConstraints:""
                                )

                        label(text:"These are the details!", font:getLabelFont())
                    }
                    detailsPanel.setVisible(false)
                    // println "detailsPanel size = " + detailsPanel.getSize()
                    detailsParent.add(detailsPanel)
                } else {
                    detailsButton.setText("Details \u25B2")
                    if (detailsParent != null && detailsPanel != null) {
                        detailsPanel.setVisible(false)
                        detailsPanel.repaint(repaintDelay)
                        detailsParent.remove(detailsPanel)
                        detailsPanel = null
                    }
                }

                pendingResize = true

                def gui = getGui()
                if (gui != null) {
                    repaint()
                } else {
                    detailsParent.repaint(repaintDelay)
                }
            }

            detailsParent = panel(constraints:"bottom, center, spanx 2, shrink 1000, dock south", opaque:false) {
                migLayout(
                        layoutConstraints:"fill" + debugString,
                        columnConstraints:"[center grow]",
                        rowConstraints:"0[]5[]0"
                        )

                detailsButton = button(text:"Details \u25BC", font:getLabelFont(), foreground:getDetailColor(), background:getBackgroundColor(), opaque:false, border:null, constraints:"wrap", actionPerformed:{
                    detailsOpen = !detailsOpen
                    updateDetails()
                })
            }

            def lastDimension = detailsParent.getSize()
            detailsParent.addComponentListener(new ComponentAdapter() {
                        public void componentResized(final ComponentEvent e) {
                            if (!pendingResize) {
                                return
                            }
                            def newDimension = e.getComponent().getSize()
                            def difference = Math.abs(newDimension.height - lastDimension.height)
                            def guiSize = getWindowSize()
                            def newSize
                            if (newDimension.height > lastDimension.height) {
                                newSize = new Dimension(Double.valueOf(guiSize.width).intValue(), Double.valueOf(guiSize.height + difference).intValue())
                            } else {
                                newSize = new Dimension(Double.valueOf(guiSize.width).intValue(), Double.valueOf(guiSize.height - difference).intValue())
                            }
                            lastDimension = newDimension
                            pendingResize = false

                            if (detailsPanel != null) {
                                detailsPanel.setVisible(true)
                            }
                            setWindowSize(newSize)
                        }
                    })

            updateDetails()

        }
    }

    public static void main(String[] args) {
        def g = new ScanGui()
        g.createAndShowGui()
    }

    @Override
    public void propertyChange(final PropertyChangeEvent evt) {
        if (evt.getPropertyName().equals(ScanReportProperties.percentageComplete.toString())) {
            final Double percentComplete = (Double)evt.getNewValue()
            System.out.println("Percent complete: " + (percentComplete * 100));
            def intPercent = new Double(percentComplete * 100).intValue()
            m_progressBar.setValue(intPercent)
            m_progressBar.setString(intPercent + "%")
        } else if (evt.getPropertyName().equals(PollerFrontEndStates.exitNecessary.toString())) {
            System.out.println("Finished scan: " + evt.getNewValue())
        } else {
            System.err.println("Unhandled property change event: " + evt.getPropertyName())
        }
    }
}

