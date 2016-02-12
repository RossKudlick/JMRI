// LogixTableActionTest.java
package jmri.jmrit.beantable;

import java.util.ResourceBundle;
import jmri.Conditional;
import jmri.InstanceManager;
import jmri.Light;
import jmri.Memory;
import jmri.Route;
import jmri.Sensor;
import jmri.SignalHead;
import jmri.Turnout;
import jmri.util.JUnitUtil;
import junit.extensions.jfcunit.TestHelper;
import junit.framework.Assert;
import junit.framework.Test;
import junit.framework.TestSuite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tests for the jmri.jmrit.beantable.LogixTableAction class
 *
 * Note that the thread executing this test must yield to allow the event thread
 * access to create and dispose of window frames. i.e. those calls that simulate
 * an actionPerformed event.
 *
 * @author	Pete Cressman Copyright 2009
 */
public class LogixTableActionTest extends jmri.util.SwingTestCase {

    static final int NUM_STATE_VARS = 20;
    static final int NUM_ACTIONS = 27;
    static final ResourceBundle rbx = ResourceBundle.getBundle("jmri.jmrit.beantable.LogixTableBundle");

    private static LogixTableAction _logixTable;

    public void testCreateLogix() throws Exception {
        //Setting these two prevents any pop up message generated by the system trying to save messages.
        jmri.InstanceManager.getDefault(jmri.UserPreferencesManager.class).setPreferenceState("jmri.managers.DefaultUserMessagePreferences", "reminder", true);
        jmri.InstanceManager.getDefault(jmri.UserPreferencesManager.class).setPreferenceState("jmri.jmrit.beantable.LogixTableAction", "remindSaveLogix", true);

        jmri.InstanceManager.store(new jmri.jmrit.signalling.EntryExitPairs(), jmri.jmrit.signalling.EntryExitPairs.class);

        try {
            _logixTable.prefMgr = jmri.InstanceManager.getDefault(jmri.UserPreferencesManager.class);
            _logixTable.actionPerformed(null);
            _logixTable.addPressed(null);
            _logixTable._addUserName.setText("TestLogix");
            _logixTable._systemName.setText("TX");
            _logixTable._autoSystemName.setSelected(false);
            _logixTable.createPressed(null);
            _logixTable.donePressed(null);
            // note: _logixTable.m.EDITCOL = BeanTableDataModel.DELETECOL
            _logixTable.m.setValueAt(rbx.getString("ButtonEdit"), 0, BeanTableDataModel.DELETECOL);
            _logixTable.newConditionalPressed(null);
            //_logixTable.helpPressed(null);
            _logixTable.conditionalUserName.setText("TestConditional");
            _logixTable.updateConditionalPressed(null);

            // now close window
            //TestHelper.disposeWindow(_logixTable.editConditionalFrame,this);
            _logixTable.newConditionalPressed(null);
            _logixTable.conditionalTableModel.setValueAt(null, 0, LogixTableAction.ConditionalTableModel.BUTTON_COLUMN);
            for (int i = 0; i < 2; i++) {
                _logixTable.addVariablePressed(null);
                _logixTable._variableTypeBox.setSelectedIndex(Conditional.ITEM_TYPE_SENSOR);
                _logixTable._variableStateBox.setSelectedIndex(i);
                _logixTable._variableNameField.setText("IS" + i);
                _logixTable.updateVariablePressed();
            }
            for (int i = 0; i < 2; i++) {
                _logixTable.addVariablePressed(null);
                _logixTable._variableTypeBox.setSelectedIndex(Conditional.ITEM_TYPE_TURNOUT);
                _logixTable._variableStateBox.setSelectedIndex(i);
                _logixTable._variableNameField.setText("Turnout" + i);
                _logixTable.updateVariablePressed();
            }
            for (int i = 0; i < 2; i++) {
                _logixTable.addVariablePressed(null);
                _logixTable._variableTypeBox.setSelectedIndex(Conditional.ITEM_TYPE_LIGHT);
                _logixTable._variableStateBox.setSelectedIndex(i);
                _logixTable._variableNameField.setText("IL" + i);
                _logixTable.updateVariablePressed();
            }
            for (int i = 0; i < 2; i++) {
                _logixTable.addVariablePressed(null);
                _logixTable._variableTypeBox.setSelectedIndex(Conditional.ITEM_TYPE_CONDITIONAL);
                _logixTable._variableStateBox.setSelectedIndex(i);
                _logixTable._variableNameField.setText("C" + i);
                _logixTable.updateVariablePressed();
            }
            /* SignalHead code changed - test cannot be done this way 
             Must implement from UI dialogs to get the right info to updateVariablePressed()
             for (int i=0; i<9; i++){
             if (i==3 || i==7) {        // lunar aspects
             continue;
             }
             _logixTable.addVariablePressed(null);
             _logixTable._variableTypeBox.setSelectedIndex(Conditional.ITEM_TYPE_SIGNALHEAD);
             _logixTable._variableStateBox.setSelectedIndex(i);
             _logixTable._variableNameField.setText("Signal"+i);
             _logixTable.updateVariablePressed();
             }
             */
            _logixTable.addActionPressed(null);
            _logixTable.cancelEditActionPressed();

            _logixTable.addActionPressed(null);
            _logixTable._actionItemTypeBox.setSelectedIndex(Conditional.ITEM_TYPE_TURNOUT);
            _logixTable._actionTypeBox.setSelectedIndex(1);         // ACTION_SET_TURNOUT
            _logixTable._actionOptionBox.setSelectedIndex(2);       // on false
            _logixTable._actionNameField.setText("Turnout3");
            _logixTable._actionBox.setSelectedIndex(0);             // Turnout.CLOSED
            _logixTable.updateActionPressed();
            /*
             // Test is done and turnoutManagerInstance gone by the time the timer goes off
             _logixTable.addActionPressed(null);
             _logixTable._actionItemTypeBox.setSelectedIndex(Conditional.ITEM_TYPE_TURNOUT);
             _logixTable._actionTypeBox.setSelectedIndex(2);         // ACTION_DELAYED_TURNOUT
             _logixTable._actionOptionBox.setSelectedIndex(1);       // on false
             _logixTable._actionNameField.setText("IT4");
             _logixTable._actionBox.setSelectedIndex(1);             // Turnout.THROWN
             _logixTable._shortActionString.setText("1");           // delay 1 sec
             _logixTable.updateActionPressed();
             */
            _logixTable.addActionPressed(null);
            _logixTable._actionItemTypeBox.setSelectedIndex(Conditional.ITEM_TYPE_TURNOUT);
            _logixTable._actionTypeBox.setSelectedIndex(3);         // ACTION_LOCK_TURNOUT
            _logixTable._actionOptionBox.setSelectedIndex(0);       // on false
            _logixTable._actionNameField.setText("Turnout5");
            _logixTable._actionBox.setSelectedIndex(2);
            _logixTable.updateActionPressed();

            _logixTable.updateConditionalPressed(null);

            // move on to another
            assertEquals("State Variable count", 8, _logixTable._curConditional.getCopyOfStateVariables().size());
            assertEquals("Action count", 2, _logixTable._curConditional.getCopyOfActions().size());
            _logixTable.newConditionalPressed(null);
            //_logixTable.helpPressed(null);
            _logixTable.conditionalUserName.setText("SecondConditional");
            _logixTable.updateConditionalPressed(null);

            Assert.assertEquals("Conditional count", 1, _logixTable._curLogix.getNumConditionals());
        //_logixTable.donePressed(null);

            // note: _logixTable.m.EDITCOL = BeanTableDataModel.DELETECOL
            //_logixTable.m.setValueAt(rbx.getString("ButtonEdit"), 0, BeanTableDataModel.DELETECOL);
            _logixTable.conditionalTableModel.setValueAt(null, 0, LogixTableAction.ConditionalTableModel.BUTTON_COLUMN);
            _logixTable.conditionalUserName.setText("FirstConditional");
            _logixTable.updateConditionalPressed(null);

            _logixTable.calculatePressed(null);
            _logixTable.donePressed(null);

            // now close window
            TestHelper.disposeWindow(_logixTable.editConditionalFrame, this);

        } catch (Exception ex) {
            ex.printStackTrace();
            throw ex;
        }
    }

    // from here down is testing infrastructure
    public LogixTableActionTest(String s) {
        super(s);
    }

    // Main entry point
    static public void main(String[] args) {
        String[] testCaseName = {"-noloading", LogixTableActionTest.class.getName()};
        junit.swingui.TestRunner.main(testCaseName);
    }

    // test suite from all defined tests
    public static Test suite() {
        Test suite = new TestSuite(LogixTableActionTest.class);
        return suite;
    }

    // The minimal setup for log4J
    protected void setUp() throws Exception {
        apps.tests.Log4JFixture.setUp();

        super.setUp();
        JUnitUtil.resetInstanceManager();
        JUnitUtil.initDefaultUserMessagePreferences();
        JUnitUtil.initInternalTurnoutManager();
        JUnitUtil.initInternalLightManager();
        JUnitUtil.initInternalSensorManager();
        JUnitUtil.initInternalSignalHeadManager();

        _logixTable = new LogixTableAction() {
            /**
             *
             */
            private static final long serialVersionUID = 6004896064187980424L;

            // skip dialog box if in edit mode, just assume OK pressed
            boolean checkEditConditional() {
                if (inEditConditionalMode) {
                    return true;
                }
                return false;
            }
        };
        assertNotNull("LogixTableAction is null!", _logixTable);        // test has begun
        _logixTable._suppressReminder = true;

        for (int i = 0; i < 10; i++) {
            Sensor s = InstanceManager.sensorManagerInstance().newSensor("IS" + i, "Sensor" + i);
            assertNotNull(i + "th Sensor is null!", s);
            Turnout t = InstanceManager.turnoutManagerInstance().newTurnout("IT" + i, "Turnout" + i);
            assertNotNull(i + "th Turnout is null!", t);
            Light l = InstanceManager.lightManagerInstance().newLight("IL" + (i), "Light" + i);
            assertNotNull(i + "th Light is null!", l);
            Conditional c = InstanceManager.conditionalManagerInstance().createNewConditional("C" + i, "Conditional" + i);
            assertNotNull(i + "th Conditional is null!", c);
            Memory m = InstanceManager.memoryManagerInstance().provideMemory("IMemory" + i);
            assertNotNull(i + "th Memory is null!", m);
            SignalHead sh = new jmri.implementation.VirtualSignalHead("Signal" + i);
            assertNotNull(i + "th SignalHead is null!", sh);
            InstanceManager.signalHeadManagerInstance().register(sh);
            Route r = new jmri.implementation.DefaultRoute("Route" + i);
            assertNotNull(i + "th Route is null!", r);
            InstanceManager.routeManagerInstance().register(r);
        }
    }

    protected void tearDown() throws Exception {
        //now close logix window
        TestHelper.disposeWindow(_logixTable.editLogixFrame, this);
        // now close logix actin window
        TestHelper.disposeWindow(_logixTable.f, this);

        JUnitUtil.resetInstanceManager();
        super.tearDown();
        apps.tests.Log4JFixture.tearDown();
    }

    private final static Logger log = LoggerFactory.getLogger(LogixTableActionTest.class.getName());
}
