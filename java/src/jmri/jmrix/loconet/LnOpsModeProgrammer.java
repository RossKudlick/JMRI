package jmri.jmrix.loconet;

import java.awt.event.ActionEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import jmri.AddressedProgrammer;
import jmri.ProgListener;
import jmri.Programmer;
import jmri.ProgrammerException;
import jmri.ProgrammingMode;
import jmri.managers.DefaultProgrammerManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provide an Ops Mode Programmer via a wrapper what works with the LocoNet
 * SlotManager object.
 *
 * @see jmri.Programmer
 * @author Bob Jacobsen Copyright (C) 2002
 */
public class LnOpsModeProgrammer implements AddressedProgrammer, LocoNetListener {

    SlotManager mSlotMgr;
    LocoNetSystemConnectionMemo memo;
    int mAddress;
    boolean mLongAddr;
    ProgListener p;
    boolean doingWrite;
    boolean boardOpSwWriteVal;
    private javax.swing.Timer bdOpSwAccessTimer = null;
    private javax.swing.Timer csOpSwAccessTimer = null;
    private javax.swing.Timer csOpSwValidTimer = null;
    private boolean csOpSwsAreValid = false;
    private cmdStnOpSwStateType cmdStnOpSwState;
    private int cmdStnOpSwNum;
    private boolean cmdStnOpSwVal;
    private LocoNetMessage lastCmdStationOpSwMessage;


    public LnOpsModeProgrammer(SlotManager pSlotMgr,
            LocoNetSystemConnectionMemo memo,
            int pAddress, boolean pLongAddr) {
        mSlotMgr = pSlotMgr;
        this.memo = memo;
        mAddress = pAddress;
        mLongAddr = pLongAddr;
        cmdStnOpSwState = cmdStnOpSwStateType.IDLE;
        lastCmdStationOpSwMessage = null;
        // register to listen
        memo.getLnTrafficController().addLocoNetListener(~0, this);
    }

    /**
     * Forward a write request to an ops-mode write operation
     */
    @Override
    @Deprecated
    public void writeCV(int CV, int val, ProgListener p) throws ProgrammerException {
        mSlotMgr.writeCVOpsMode(CV, val, p, mAddress, mLongAddr);
    }

    @Override
    @Deprecated
    public void readCV(int CV, ProgListener p) throws ProgrammerException {
        mSlotMgr.readCVOpsMode(CV, p, mAddress, mLongAddr);
    }

    @Override
    public void writeCV(String CV, int val, ProgListener p) throws ProgrammerException {
        this.p = null;
        // Check mode
        LocoNetMessage m;
        if (getMode().equals(LnProgrammerManager.LOCONETCSOPSWMODE)) {
            if (csOpSwAccessTimer == null) {
                initializeCsOpSwAccessTimer();
                initializeCsOpSwValidTimer();
            }

            // Command Station OpSws are handled via slot 0x7f.
            this.p = p;
            doingWrite = true;
            log.debug("write Command Station OpSw{} as {}", CV, (val>0)?"c":"t");
            String[] parts = CV.split("\\.");
            int typeWord = 0;
            if ((typeWord = Integer.parseInt(parts[0])) == 1) {
                if (!updateCmdStnOpSw(Integer.parseInt(parts[1]),
                        (val>0)?true:false)) {
                    ProgListener temp = p;
                    p = null;
                    if (temp != null) {
                        temp.programmingOpReply(0, ProgListener.ProgrammerBusy);
                    }
                }
            } else {
                ProgListener temp = p;
                p = null;
                if (temp != null) {
                    temp.programmingOpReply(0,ProgListener.NotImplemented);
                }
            }
        } else if (getMode().equals(LnProgrammerManager.LOCONETBDOPSWMODE)) {
            /**
             * CV format is e.g. "113.12" where the first part defines the
             * typeword for the specific board type and the second is the specific bit number
             * Known values:
             * <UL>
             * <LI>0x70 112 - PM4
             * <LI>0x71 113 - BDL16
             * <LI>0x72 114 - SE8
             * <LI>0x73 115 - DS64
             * </ul>
             */
            if (bdOpSwAccessTimer == null) {
                initiializeBdOpsAccessTimer();
            }
            this.p = p;
            doingWrite = true;
            // Board programming mode
            log.debug("write CV \"{}\" to {} addr:{}", CV, val, mAddress);
            String[] parts = CV.split("\\.");
            int typeWord = Integer.parseInt(parts[0]);
            int state = Integer.parseInt(parts[parts.length>1 ? 1 : 0]);

            // make message
            m = new LocoNetMessage(6);
            m.setOpCode(LnConstants.OPC_MULTI_SENSE);
            int element = 0x72;
            if ((mAddress & 0x80) != 0) {
                element |= 1;
            }
            m.setElement(1, element);
            m.setElement(2, (mAddress-1) & 0x7F);
            m.setElement(3, typeWord);
            int loc = (state - 1) / 8;
            int bit = (state - 1) - loc * 8;
            m.setElement(4, loc * 16 + bit * 2  + (val&0x01));

            // save a copy of the written value for use during reply
            boardOpSwWriteVal = ((val & 0x01) == 1);

            log.debug("  Message {}", m);
            memo.getLnTrafficController().sendLocoNetMessage(m);

            bdOpSwAccessTimer.start();
        } else if (getMode().equals(LnProgrammerManager.LOCONETSV1MODE)) {
            this.p = p;
            doingWrite = true;
            // SV1 mode
            log.debug("write CV \"{}\" to {} addr:{}", CV, val, mAddress);

            // make message
            int locoIOAddress = mAddress;
            int locoIOSubAddress = ((mAddress+256)/256)&0x7F;
            m = jmri.jmrix.loconet.locoio.LocoIO.writeCV(locoIOAddress, locoIOSubAddress, decodeCvNum(CV), val);
            // force version 1 tag
            m.setElement(4, 0x01);

            log.debug("  Message {}", m);
            memo.getLnTrafficController().sendLocoNetMessage(m);
        } else if (getMode().equals(LnProgrammerManager.LOCONETSV2MODE)) {
            this.p = p;
            // SV2 mode
            log.debug("write CV \"{}\" to {} addr:{}", CV, val, mAddress);
            // make message
            m = new LocoNetMessage(16);
            loadSV2MessageFormat(m, mAddress, decodeCvNum(CV), val);
            m.setElement(3, 0x01); // 1 byte write
            log.debug("  Message {}", m);
            memo.getLnTrafficController().sendLocoNetMessage(m);
        } else {
            // DCC ops mode
            writeCV(Integer.parseInt(CV), val, p);
        }
    }

    @Override
    public void readCV(String CV, ProgListener p) throws ProgrammerException {
        this.p = null;
        // Check mode
        String[] parts;
        LocoNetMessage m;
        if (getMode().equals(LnProgrammerManager.LOCONETCSOPSWMODE)) {
            if (csOpSwAccessTimer == null) {
                initializeCsOpSwAccessTimer();
                initializeCsOpSwValidTimer();
            }

            // Command Station OpSws are handled via slot 0x7f.
            this.p = p;
            doingWrite = false;
            log.debug("read Command Station OpSw{}", CV);
            parts = CV.split("\\.");
            ProgListener temp = p;
            if (parts.length != 2) {
                p = null;
                if (temp != null) {
                    temp.programmingOpReply(0,ProgListener.NotImplemented);
                }
            }
            log.trace("splitting CV: {} becomes {} and {}", CV, parts[0], parts[1]);
            int typeWord;
            if ((typeWord = Integer.parseInt(parts[0])) == 1) {
                log.trace("Valid typeWord = 1; attempting to read OpSw{}.", Integer.parseInt(parts[1]));
                log.trace("starting from state {}", cmdStnOpSwState);
                readCmdStationOpSw(Integer.parseInt(parts[1]));
            } else {
                p = null;
                log.trace("readCV: bad variable number intial value: {} ",typeWord);
                temp.programmingOpReply(0,ProgListener.NotImplemented);
            }
        } else if (getMode().equals(LnProgrammerManager.LOCONETBDOPSWMODE)) {
            /**
             * CV format is e.g. "113.12" where the first part defines the
             * typeword for the specific board type and the second is the specific bit number
             * Known values:
             * <UL>
             * <LI>0x70 112 - PM4
             * <LI>0x71 113 - BDL16
             * <LI>0x72 114 - SE8
             * <LI>0x73 115 - DS64
             * </ul>
             */
            if (bdOpSwAccessTimer == null) {
                initiializeBdOpsAccessTimer();
            }
            this.p = p;
            doingWrite = false;
            // Board programming mode
            log.debug("read CV \"{}\" addr:{}", CV, mAddress);
            parts = CV.split("\\.");
            int typeWord = Integer.parseInt(parts[0]);
            int state = Integer.parseInt(parts[parts.length>1 ? 1 : 0]);

            // make message
            m = new LocoNetMessage(6);
            m.setOpCode(LnConstants.OPC_MULTI_SENSE);
            int element = 0x62;
            if ((mAddress & 0x80) != 0) {
                element |= 1;
            }
            m.setElement(1, element);
            m.setElement(2, (mAddress-1) & 0x7F);
            m.setElement(3, typeWord);
            int loc = (state - 1) / 8;
            int bit = (state - 1) - loc * 8;
            m.setElement(4, loc * 16 + bit * 2);

            log.debug("  Message {}", m);
            memo.getLnTrafficController().sendLocoNetMessage(m);
            bdOpSwAccessTimer.start();

        } else if (getMode().equals(LnProgrammerManager.LOCONETSV1MODE)) {
            this.p = p;
            doingWrite = false;
            // SV1 mode
            log.debug("read CV \"{}\" addr:{}", CV, mAddress);

            // make message
            int locoIOAddress = mAddress&0xFF;
            int locoIOSubAddress = ((mAddress+256)/256)&0x7F;
            m = jmri.jmrix.loconet.locoio.LocoIO.readCV(locoIOAddress, locoIOSubAddress, decodeCvNum(CV));
            // force version 1 tag
            m.setElement(4, 0x01);

            log.debug("  Message {}", m);
            memo.getLnTrafficController().sendLocoNetMessage(m);
        } else if (getMode().equals(LnProgrammerManager.LOCONETSV2MODE)) {
            this.p = p;
            // SV2 mode
            log.debug("read CV \"{}\" addr:{}", CV, mAddress, mAddress);
            // make message
            m = new LocoNetMessage(16);
            loadSV2MessageFormat(m, mAddress, decodeCvNum(CV), 0);
            m.setElement(3, 0x02); // 1 byte read
            log.debug("  Message {}", m);
            memo.getLnTrafficController().sendLocoNetMessage(m);
        } else {
            // DCC ops mode
            readCV(Integer.parseInt(CV), p);
        }
    }

    @Override
    @Deprecated
    @SuppressWarnings("deprecation") // parent Programmer method deprecated, will remove at same time
    public final void confirmCV(int CV, int val, ProgListener p) throws ProgrammerException {
        confirmCV(""+CV, val, p);
    }

    @Override
    public void confirmCV(String CV, int val, ProgListener p) throws ProgrammerException {
        this.p = null;
        // Check mode
        if ((getMode().equals(LnProgrammerManager.LOCONETCSOPSWMODE)) ||
                (getMode().equals(LnProgrammerManager.LOCONETBDOPSWMODE))) {
            readCV(CV, p);
        }
        else if (getMode().equals(LnProgrammerManager.LOCONETSV2MODE)) {
            // SV2 mode
            log.warn("confirm CV \"{}\" addr:{} in SV2 mode not implemented", CV, mAddress);
            p.programmingOpReply(0, ProgListener.UnknownError);
        } else {
            // DCC ops mode
            mSlotMgr.confirmCVOpsMode(CV, val, p, mAddress, mLongAddr);
        }
    }

    @Override
    public void message(LocoNetMessage m) {

        log.debug("LocoNet message received: {}",m);
        if (getMode().equals(LnProgrammerManager.LOCONETCSOPSWMODE)) {
            boolean val;
            if ((m.getOpCode() == LnConstants.OPC_SL_RD_DATA) &&
                    (m.getElement(1) == 0x0E) &&
                    (m.getElement(2) == 0x7f)) {
                log.debug("got slot 127 read data");
                if (cmdStnOpSwState == cmdStnOpSwStateType.QUERY) {
                    log.debug("got slot 127 read data in response to OpSw query");
                    csOpSwAccessTimer.stop();
                    cmdStnOpSwState = cmdStnOpSwStateType.HAS_STATE;
                    lastCmdStationOpSwMessage = m;  // save a copy of the LocoNet message
                    csOpSwsAreValid = true;
                    csOpSwValidTimer.start();   // start the "valid data" timer
                    if (doingWrite == true) {
                        log.debug("now can finish the write by updating the correct bit...");
                        finishTheWrite();
                    } else {
                        val = extractCmdStnOpSw(m, cmdStnOpSwNum);
                        log.debug("now can return the extracted OpSw{} read data ({}) to the programmer", cmdStnOpSwNum, val);
                        ProgListener temp = p;
                        p = null;
                        if (temp != null) {
                            log.debug("Returning data");
                            temp.programmingOpReply(val?1:0, ProgListener.OK);
                        } else {
                            log.debug("no programmer to return the data to.");
                        }
                    }
                } else if (cmdStnOpSwState == cmdStnOpSwStateType.QUERY_BEFORE_WRITE) {
                    log.debug("hve received OpSw query before a write; now can process the data modification");
                    csOpSwAccessTimer.stop();
                    LocoNetMessage m2 = updateOpSwVal(m, cmdStnOpSwNum,
                            cmdStnOpSwVal);
                    cmdStnOpSwState = cmdStnOpSwStateType.WRITE;
                    memo.getLnTrafficController().sendLocoNetMessage(m2);
                    csOpSwAccessTimer.start();
                }
            } else if ((m.getOpCode() == LnConstants.OPC_LONG_ACK) &&
                    (m.getElement(1) == 0x6f) &&
                    (m.getElement(2) == 0x7f) &&
                    (cmdStnOpSwState == cmdStnOpSwStateType.WRITE)) {
                csOpSwAccessTimer.stop();
                cmdStnOpSwState = cmdStnOpSwStateType.HAS_STATE;
                val = extractCmdStnOpSw(lastCmdStationOpSwMessage, cmdStnOpSwNum);
                ProgListener temp = p;
                p = null;
                if (temp != null) {
                    temp.programmingOpReply(val?1:0, ProgListener.OK);
                }
            }
        }
        else if (getMode().equals(LnProgrammerManager.LOCONETBDOPSWMODE)) {

            // are we reading? If not, ignore
            if (p == null) {
                log.warn("received board-program reply message with no reply object: {}", m);
                return;
            }

            // check for right type, unit
            if (m.getOpCode() != 0xb4
                    || ((m.getElement(1) != 0x00) && (m.getElement(1) != 0x50))) {
                return;
            }
            // got a message that is LONG_ACK reply to an BdOpsSw access
            bdOpSwAccessTimer.stop();    // kill the timeout timer

            // LACK with 0x00 or 0x50 in byte 1; assume its to us.

            if (doingWrite) {

                int code = ProgListener.OK;
                int val = boardOpSwWriteVal?1:0;

                ProgListener temp = p;
                p = null;
                if (temp != null) {
                    temp.programmingOpReply(val, code);
                }

                return;
            }

            int val = 0;
            if ((m.getElement(2) & 0x20) != 0) {
                val = 1;
            }

            // successful read if LACK return status is not 0x7F
            int code = ProgListener.OK;
            if ((m.getElement(2) == 0x7f)) {
                code = ProgListener.UnknownError;
            }

            ProgListener temp = p;
            p = null;
            if (temp != null) {
                temp.programmingOpReply(val, code);
            }


        } else if (getMode().equals(LnProgrammerManager.LOCONETSV1MODE)) {
            // see if reply to LNSV 1 or LNSV2 request
            if (((m.getElement( 0) & 0xFF) != 0xE5) ||
                    ((m.getElement( 1) & 0xFF) != 0x10) ||
                    ((m.getElement( 4) & 0xFF) != 0x01) || // format 1
                    ((m.getElement( 5) & 0x70) != 0x00)) {
                return;
            }

            // check for src address (?) moved to 0x50
            // this might not be the right way to tell....
            if ((m.getElement(3) & 0x7F) != 0x50) {
                return;
            }

            // more checks needed? E.g. addresses?

            // Mode 1 return data comes back in
            // byte index 12, with the MSB in 0x01 of byte index 10
            //

            // check pending activity
            if (p == null) {
                log.warn("received SV reply message with no reply object: {}", m);
                return;
            } else {
                log.debug("returning SV programming reply: {}", m);
                int code = ProgListener.OK;
                int val;
                if (doingWrite) {
                    val = m.getPeerXfrData()[7];
                } else {
                    val = m.getPeerXfrData()[5];
                }
                ProgListener temp = p;
                p = null;
                if (temp != null) {
                    temp.programmingOpReply(val, code);
                }
            }
        } else if (getMode().equals(LnProgrammerManager.LOCONETSV2MODE)) {
            // see if reply to LNSV 1 or LNSV2 request
            if (((m.getElement( 0) & 0xFF) != 0xE5) ||
                    ((m.getElement( 1) & 0xFF) != 0x10) ||
                    ((m.getElement( 3) != 0x41) && (m.getElement(3) != 0x42)) || // need a "Write One Reply", or a "Read One Reply"
                    ((m.getElement( 4) & 0xFF) != 0x02) || // format 2)
                    ((m.getElement( 5) & 0x70) != 0x10) || // need SVX1 high nibble = 1
                    ((m.getElement(10) & 0x70) != 0x10) // need SVX2 high nibble = 1
                    ) {
                return;
            }

            // more checks needed? E.g. addresses?

            // return reply
            if (p == null) {
                log.error("received SV reply message with no reply object: {}", m);
                return;
            } else {
                log.debug("returning SV programming reply: {}", m);
                int code = ProgListener.OK;
                int val = (m.getElement(11)&0x7F)|(((m.getElement(10)&0x01) != 0x00)? 0x80:0x00);

                ProgListener temp = p;
                p = null;
                temp.programmingOpReply(val, code);
            }
        }
    }

    int decodeCvNum(String CV) {
        try {
            return Integer.valueOf(CV).intValue();
        } catch (java.lang.NumberFormatException e) {
            return 0;
        }
    }

    void loadSV2MessageFormat(LocoNetMessage m, int mAddress, int cvAddr, int data) {
        m.setElement(0, 0xE5);
        m.setElement(1, 0x10);
        m.setElement(2, 0x01);
        // 3 SV_CMD to be filled in later
        m.setElement(4, 0x02);
        // 5 will come back to SVX1
        m.setElement(6, mAddress&0xFF);
        m.setElement(7, (mAddress>>8)&0xFF);
        m.setElement(8, cvAddr&0xFF);
        m.setElement(9, (cvAddr/256)&0xFF);

        // set SVX1
        int svx1 = 0x10
                    |((m.getElement(6)&0x80) != 0 ? 0x01 : 0)  // DST_L
                    |((m.getElement(7)&0x80) != 0 ? 0x02 : 0)  // DST_L
                    |((m.getElement(8)&0x80) != 0 ? 0x04 : 0)  // DST_L
                    |((m.getElement(9)&0x80) != 0 ? 0x08 : 0); // SV_ADRH
        m.setElement(5, svx1);
        m.setElement(6, m.getElement(6)&0x7F);
        m.setElement(7, m.getElement(7)&0x7F);
        m.setElement(8, m.getElement(8)&0x7F);
        m.setElement(9, m.getElement(9)&0x7F);

        // 10 will come back to SVX2
        m.setElement(11, data&0xFF);
        m.setElement(12, (data>>8)&0xFF);
        m.setElement(13, (data>>16)&0xFF);
        m.setElement(14, (data>>24)&0xFF);

        // set SVX2
        int svx2 = 0x10
                    |((m.getElement(11)&0x80) != 0 ? 0x01 : 0)
                    |((m.getElement(12)&0x80) != 0 ? 0x02 : 0)
                    |((m.getElement(13)&0x80) != 0 ? 0x04 : 0)
                    |((m.getElement(14)&0x80) != 0 ? 0x08 : 0);
        m.setElement(10, svx2);
        m.setElement(11, m.getElement(11)&0x7F);
        m.setElement(12, m.getElement(12)&0x7F);
        m.setElement(13, m.getElement(13)&0x7F);
        m.setElement(14, m.getElement(14)&0x7F);
    }

    // handle mode
    protected ProgrammingMode mode = ProgrammingMode.OPSBYTEMODE;

    @Override
    public final void setMode(ProgrammingMode m) {
        if (getSupportedModes().contains(m)) {
            mode = m;
            notifyPropertyChange("Mode", mode, m); // NOI18N
        } else {
            throw new IllegalArgumentException("Invalid requested mode: " + m); // NOI18N
        }
    }

    @Override
    public final ProgrammingMode getMode() {
        return mode;
    }

    /**
     * Types implemented here.
     */
    @Override
    public List<ProgrammingMode> getSupportedModes() {
        List<ProgrammingMode> ret = new ArrayList<>(4);
        ret.add(ProgrammingMode.OPSBYTEMODE);
        ret.add(LnProgrammerManager.LOCONETSV1MODE);
        ret.add(LnProgrammerManager.LOCONETSV2MODE);
        ret.add(LnProgrammerManager.LOCONETBDOPSWMODE);
        ret.add(LnProgrammerManager.LOCONETCSOPSWMODE);
        return ret;
    }

    /**
     * Confirmation mode by programming mode; not that this doesn't
     * yet know whether BDL168 hardware is present to allow DecoderReply
     * to function; that should be a preference eventually.  See also DCS240...
     *
     * @param addr CV address ignored, as there's no variance with this in LocoNet
     * @return Depends on programming mode
     */
    @Nonnull
    @Override
    public Programmer.WriteConfirmMode getWriteConfirmMode(String addr) {
        if (getMode().equals(ProgrammingMode.OPSBYTEMODE)) {
            return WriteConfirmMode.NotVerified;
        }
        return WriteConfirmMode.DecoderReply;
    }

    /**
     * Provide a {@link java.beans.PropertyChangeSupport} helper.
     */
    private final PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);

    /**
     * Add a PropertyChangeListener to the listener list.
     *
     * @param listener The PropertyChangeListener to be added
     */
    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }

    protected void notifyPropertyChange(String key, Object oldValue, Object value) {
        propertyChangeSupport.firePropertyChange(key, oldValue, value);
    }

    /**
     * Can this ops-mode programmer read back values? Yes, if transponding
     * hardware is present, but we don't check that here.
     *
     * @return always true
     */
    @Override
    public boolean getCanRead() {
        return true;
    }

    @Override
    public boolean getCanRead(String addr) {
        return getCanRead();
    }

    @Override
    public boolean getCanWrite() {
        return true;
    }

    @Override
    public boolean getCanWrite(String addr) {
        return getCanWrite() && Integer.parseInt(addr) <= 1024;
    }

    @Override
    public String decodeErrorCode(int i) {
        return mSlotMgr.decodeErrorCode(i);
    }

    @Override
    public boolean getLongAddress() {
        return mLongAddr;
    }

    @Override
    public int getAddressNumber() {
        return mAddress;
    }

    @Override
    public String getAddress() {
        return "" + getAddressNumber() + " " + getLongAddress();
    }

    void initiializeBdOpsAccessTimer() {
        if (bdOpSwAccessTimer == null) {
            bdOpSwAccessTimer = new javax.swing.Timer(1000, (ActionEvent e) -> {
                ProgListener temp = p;
                p = null;
                temp.programmingOpReply(0, ProgListener.FailedTimeout);
            });
        bdOpSwAccessTimer.setInitialDelay(1000);
        bdOpSwAccessTimer.setRepeats(false);
        }
    }


    public void readCmdStationOpSw(int cv) {
        log.debug("readCmdStationOpSw: state is {}, have valid is ",cmdStnOpSwState, csOpSwsAreValid?"true":"false");
        if ((cmdStnOpSwState == cmdStnOpSwStateType.HAS_STATE) &&
                (csOpSwsAreValid == true)) {
            // can re-use previous state - it has not "expired" due to time since read.
            log.debug("readCmdStationOpSw: returning state from previously-stored state for OpSw{}", cv);
            returnCmdStationOpSwVal(cv);
            return;
        } else if ((cmdStnOpSwState == cmdStnOpSwStateType.IDLE) ||
                (cmdStnOpSwState == cmdStnOpSwStateType.HAS_STATE)) {
            // do not have valid data or old data has "expired" due to time since read.
            log.debug("readCmdStationOpSw: attempting to read some CVs");
            updateCmdStnOpSw(cv,false);
            return;
        } else {
            log.debug("readCmdStationOpSw: aborting - cmdStnOpSwState is odd: {}", cmdStnOpSwState);
            ProgListener temp = p;
            p = null;
            temp.programmingOpReply(0, ProgListener.ProgrammerBusy);
        }
    }

    public void returnCmdStationOpSwVal(int cmdStnOpSwNum) {
        boolean returnVal = extractCmdStnOpSw(lastCmdStationOpSwMessage, cmdStnOpSwNum);
        log.debug("returnCmdStationOpSwVal: Returning OpSw{} value of {}", cmdStnOpSwNum, returnVal);
        p.programmingOpReply(returnVal?1:0, ProgListener.OK);
    }

    public boolean updateCmdStnOpSw(int opSwNum, boolean val) {
        if (cmdStnOpSwState == cmdStnOpSwStateType.HAS_STATE) {
            if (!doingWrite) {
                log.debug("updateCmdStnOpSw: should already have OpSw values from previous read.");
                return false;
            } else {
                cmdStnOpSwVal = val;
                cmdStnOpSwNum = opSwNum;
                finishTheWrite();
                return true;
            }
        }
        if (cmdStnOpSwState != cmdStnOpSwStateType.IDLE)  {
            log.debug("updateCmdStnOpSw: cannot query OpSw values from state {}", cmdStnOpSwState);
            return false;
        }
        log.debug("updateCmdStnOpSw: attempting to query the OpSws when state = ");
        cmdStnOpSwState = cmdStnOpSwStateType.QUERY;
        cmdStnOpSwNum = opSwNum;
        cmdStnOpSwVal = val;
        int[] contents = {LnConstants.OPC_RQ_SL_DATA, 0x7F, 0x0, 0x0};
        memo.getLnTrafficController().sendLocoNetMessage(new LocoNetMessage(contents));
        csOpSwAccessTimer.start();

        return true;
    }

    public boolean extractCmdStnOpSw(LocoNetMessage m, int cmdStnOpSwNum) {
        if (m.getNumDataElements()<14) {
            csOpSwAccessTimer.stop();
            csOpSwValidTimer.stop();
            ProgListener temp = p;
            p = null;
            if (temp != null) {
                temp.programmingOpReply(0, ProgListener.UnknownError);
            }
            cmdStnOpSwState = cmdStnOpSwStateType.IDLE;
        }
        int messageByte = 0;
        messageByte = 2 + ((cmdStnOpSwNum+7) >> 3);
        if (cmdStnOpSwNum > 32) {
            messageByte ++;
        }
        int val = m.getElement(messageByte);
        val = (val >> ((cmdStnOpSwNum - 1) & 0x7)) & 0x1;
        return (val == 1);
    }

    public LocoNetMessage updateOpSwVal(LocoNetMessage m, int cmdStnOpSwNum, boolean cmdStnOpSwVal) {
        int messageByte = 0;
        log.debug("updateOpSwVal: OpSw{} = {}", cmdStnOpSwNum, cmdStnOpSwVal);
        messageByte = 2 + ((cmdStnOpSwNum+7) >> 3);
        if (cmdStnOpSwNum > 32) {
            messageByte ++;
        }
        int val = m.getElement(messageByte);
        log.debug("updateOpSwVal: working with messageByte {}, value is {}", messageByte, val);
        val &= ~(1 << ((cmdStnOpSwNum - 1) & 0x7));
        if (cmdStnOpSwVal == true) {
            val |= 1 << ((cmdStnOpSwNum - 1) & 0x7);
        }
        LocoNetMessage m2 = m;
        log.debug("updateOpSwVal: new value for messageByte{} is {}", messageByte, val);
        m2.setElement(messageByte, val);
        return m2;
    }

    private void finishTheWrite() {
        cmdStnOpSwState = cmdStnOpSwStateType.WRITE;
        LocoNetMessage m2 = updateOpSwVal(lastCmdStationOpSwMessage,
                cmdStnOpSwNum,
                cmdStnOpSwVal);
        log.debug("gonna send message {}", m2.toString());
        m2.setOpCode(LnConstants.OPC_WR_SL_DATA);
        memo.getLnTrafficController().sendLocoNetMessage(m2);
        lastCmdStationOpSwMessage = m2;
        csOpSwAccessTimer.start();
    }

    private enum cmdStnOpSwStateType {
        IDLE,
        QUERY,
        QUERY_BEFORE_WRITE,
        WRITE,
        HAS_STATE}

    void initializeCsOpSwAccessTimer() {
        if (csOpSwAccessTimer == null) {
            csOpSwAccessTimer = new javax.swing.Timer(500, (ActionEvent e) -> {
                log.debug("csOpSwAccessTimer timed out!");
                ProgListener temp = p;
                p = null;
                if (temp != null) {
                    temp.programmingOpReply(0, ProgListener.FailedTimeout);
                }
            });
        csOpSwAccessTimer.setRepeats(false);
        }
    }

    void initializeCsOpSwValidTimer() {
        if (csOpSwValidTimer == null) {
            csOpSwValidTimer = new javax.swing.Timer(10000, (ActionEvent e) -> {
                log.debug("csOpSwValidTimer timed out; invalidating held data!");
                csOpSwsAreValid = false;
                cmdStnOpSwState = cmdStnOpSwStateType.IDLE;
                });
       csOpSwValidTimer.setRepeats(false);
        }
    }

    // initialize logging
    private final static Logger log = LoggerFactory.getLogger(LnOpsModeProgrammer.class);

}
