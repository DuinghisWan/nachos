package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

import java.util.Set;
import java.util.Vector;

import java.io.EOFException;
// import com.sun.org.apache.bcel.internal.generic.ReturnaddressType;
import java.util.*;

/**
 * Encapsulates the state of a user process that is not contained in its user
 * thread (or threads). This includes its address translation state, a file
 * table, and information about the program being executed.
 *
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 *
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
    /**
     * Allocate a new process.
     */
    public UserProcess() {
        
        initFileDescriptors();

        childProcesses = new LinkedList<UserProcess>();
        parentProcess = null;

        processID = UserKernel.processID;
        UserKernel.processID += 1;

        Statuses = new HashMap<Integer, Integer>();
        hmLock = new Lock();
    }

    /**
     * Allocate and return a new process of the correct class. The class name is
     * specified by the <tt>nachos.conf</tt> key <tt>Kernel.processClassName</tt>.
     *
     * @return a new process of the correct class.
     */
    public static UserProcess newUserProcess() {
        return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
    }

    /**
     * Execute the specified program with the specified arguments. Attempts to load
     * the program, and then forks a thread to run it.
     *
     * @param name the name of the file containing the executable.
     * @param args the arguments to pass to the executable.
     * @return <tt>true</tt> if the program was successfully executed.
     */
    public boolean execute(String name, String[] args) {
        Lib.debug(dbgProcess, "UserProcess.execute()");

        if (!load(name, args))
            return false;

        thread = new UThread(this);
        thread.setName(name).fork();

        return true;
    }

    /**
     * Save the state of this process in preparation for a context switch. Called by
     * <tt>UThread.saveState()</tt>.
     */
    public void saveState() {
        Lib.debug(dbgProcess, "UserProcess.saveState()");
    }

    /**
     * Restore the state of this process after a context switch. Called by
     * <tt>UThread.restoreState()</tt>.
     */
    public void restoreState() {
        Lib.debug(dbgProcess, "UserProcess.restoreState()");
        Lib.debug(dbgProcess, "Page Table Size: " + String.valueOf(pageTable.length));
        
        if (Machine.processor().hasTLB() == false)

            Machine.processor().setPageTable(pageTable);

        else {
            int tlbSize = Machine.processor().getTLBSize();

            Lib.debug(dbgProcess, "TLB Size: " + String.valueOf(tlbSize));

            for (int index = 0; index < tlbSize; index++) {
                Machine.processor().writeTLBEntry(index, pageTable[index]);
            }

        }
    }

	// translate virtual address to physical address, or returns -1 if this doesn't work
	public int translate (int vaddr) {
		//check if virtual address is in correct range
		//if(vaddr < 0 || vaddr >= numPages*pageSize) {
		//	return -1;
		//}

		int virtPageNum = Machine.processor().pageFromAddress(vaddr); // virtual page number
		//if (!pageTable[virtPageNum].valid) {
		//	return -1;
		//}
		int physPageNum = pageTable[virtPageNum].ppn;	// virtual page number is index???
		int offset = Machine.processor().offsetFromAddress(vaddr);
		int paddr = Machine.processor().makeAddress(physPageNum, offset);

		return paddr;
	}

    /**
     * Read a null-terminated string from this process's virtual memory. Read at
     * most <tt>maxLength + 1</tt> bytes from the specified address, search for the
     * null terminator, and convert it to a <tt>java.lang.String</tt>, without
     * including the null terminator. If no null terminator is found, returns
     * <tt>null</tt>.
     *
     * @param vaddr     the starting virtual address of the null-terminated string.
     * @param maxLength the maximum number of characters in the string, not
     *                  including the null terminator.
     * @return the string read, or <tt>null</tt> if no null terminator was found.
     */
    public String readVirtualMemoryString(int vaddr, int maxLength) {
        Lib.debug(dbgProcess, "UserProcess.readVirtualMemoryString()");
        
        Lib.assertTrue(maxLength >= 0);

        byte[] bytes = new byte[maxLength + 1];

        int bytesRead = readVirtualMemory(vaddr, bytes);

        for (int length = 0; length < bytesRead; length++) {
            if (bytes[length] == 0)
                return new String(bytes, 0, length);
        }

        return null;
    }

    /**
     * Transfer data from this process's virtual memory to all of the specified
     * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param vaddr the first byte of virtual memory to read.
     * @param data  the array where the data will be stored.
     * @return the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data) {
        return readVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from this process's virtual memory to the specified array. This
     * method handles address translation details. This method must <i>not</i>
     * destroy the current process if an error occurs, but instead should return the
     * number of bytes successfully copied (or zero if no data could be copied).
     *
     * @param vaddr  the first byte of virtual memory to read.
     * @param data   the array where the data will be stored.
     * @param offset the first byte to write in the array.
     * @param length the number of bytes to transfer from virtual memory to the
     *               array.
     * @return the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);


		byte[] memory = Machine.processor().getMemory();

		if(vaddr < 0 || vaddr >= numPages*pageSize) {
			return 0;
		}

		int NumBytesTransf = 0;
		//while () {
		for (NumBytesTransf = 0; NumBytesTransf < length; NumBytesTransf++) {

			int virtPageNum = Machine.processor().pageFromAddress(vaddr + NumBytesTransf );
			if (virtPageNum < 0 || virtPageNum >=  numPages) {
				break;
			}
			int paddr = translate(vaddr + NumBytesTransf);

			if (paddr < 0) {
				break;	// this might give some issues. remove checks in translate if not working
			}

			System.arraycopy(memory, paddr, data, offset + NumBytesTransf, 1);
			pageTable[virtPageNum].used = true;
			
		}

		return NumBytesTransf;
    }

    /**
     * Transfer all data from the specified array to this process's virtual memory.
     * Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param vaddr the first byte of virtual memory to write.
     * @param data  the array containing the data to transfer.
     * @return the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data) {
        return writeVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from the specified array to this process's virtual memory. This
     * method handles address translation details. This method must <i>not</i>
     * destroy the current process if an error occurs, but instead should return the
     * number of bytes successfully copied (or zero if no data could be copied).
     *
     * @param vaddr  the first byte of virtual memory to write.
     * @param data   the array containing the data to transfer.
     * @param offset the first byte to transfer from the array.
     * @param length the number of bytes to transfer from the array to virtual
     *               memory.
     * @return the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {

		Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);
		byte[] memory = Machine.processor().getMemory();

		if(vaddr < 0 || vaddr >= numPages*pageSize) {
			return 0;
		}

		int NumBytesTransf = 0;
		//while () {
		for (NumBytesTransf = 0; NumBytesTransf < length; NumBytesTransf++) {

			int virtPageNum = Machine.processor().pageFromAddress(vaddr + NumBytesTransf );
			if (virtPageNum < 0 || virtPageNum >=  numPages) {
				break;
			}
			int paddr = translate(vaddr + NumBytesTransf);

			if (paddr < 0) {
				break;	// this might give some issues. remove checks in translate if not working
			}

			System.arraycopy(data, offset + NumBytesTransf, memory, paddr, 1);
			pageTable[virtPageNum].used = true;
			//System.arraycopy ();
			//NumBytesTransf++;
		}

		return NumBytesTransf;

    }

    /**
     * Load the executable with the specified name into this process, and prepare to
     * pass it the specified arguments. Opens the executable, reads its header
     * information, and copies sections and arguments into this process's virtual
     * memory.
     *
     * @param name the name of the file containing the executable.
     * @param args the arguments to pass to the executable.
     * @return <tt>true</tt> if the executable was successfully loaded.
     */
    private boolean load(String name, String[] args) {
        Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

        OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
        // Lib.assertTrue(executable != null);
        if (executable == null) {
            Lib.debug(dbgProcess, "\topen failed");
            // Lib.assertTrue(false);
            return false;
        }

        try {
            coff = new Coff(executable);
        } catch (EOFException e) {
            executable.close();
            Lib.debug(dbgProcess, "\tcoff load failed");
            // Lib.assertTrue(false);
            return false;
        }
        Lib.assertTrue(coff != null);

        // make sure the sections are contiguous and start at page 0
        numPages = 0;
        for (int s = 0; s < coff.getNumSections(); s++) {
            CoffSection section = coff.getSection(s);
            Lib.assertTrue(section.getFirstVPN() == numPages);
            if (section.getFirstVPN() != numPages) {
                coff.close();
                Lib.debug(dbgProcess, "\tfragmented executable");
                // Lib.assertTrue(false);
                return false;
            }
            numPages += section.getLength();
        }

        // make sure the argv array will fit in one page
        byte[][] argv = new byte[args.length][];
        int argsSize = 0;
        for (int i = 0; i < args.length; i++) {
            argv[i] = args[i].getBytes();
            // 4 bytes for argv[] pointer; then string plus one for null byte
            argsSize += 4 + argv[i].length + 1;
        }
        if (argsSize > pageSize) {
            coff.close();
            Lib.debug(dbgProcess, "\targuments too long");
            // Lib.assertTrue(false);
            return false;
        }

        // program counter initially points at the program entry point
        initialPC = coff.getEntryPoint();

        // next comes the stack; stack pointer initially points to top of it
        numPages += stackPages;
        initialSP = numPages * pageSize;

        // and finally reserve 1 page for arguments
        numPages++;

        Lib.assertTrue(numPages > 0);
        pageTable = new TranslationEntry[numPages];
        UserKernel.pageLock.acquire();
        for (int i = 0; i < numPages; i++) {
            pageTable[i] = new TranslationEntry(i, UserKernel.freePages.pop(), true, false, false, false);
        }
        UserKernel.pageLock.release();

        if (!loadSections())
            return false;

        // store arguments in last page
        int entryOffset = (numPages - 1) * pageSize;
        int stringOffset = entryOffset + args.length * 4;

        this.argc = args.length;
        this.argv = entryOffset;

        for (int i = 0; i < argv.length; i++) {
            byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
            Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
            entryOffset += 4;
            Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
            stringOffset += argv[i].length;
            Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] { 0 }) == 1);
            stringOffset += 1;
        }

        return true;
    }

    /**
     * Allocates memory for this process, and loads the COFF sections into memory.
     * If this returns successfully, the process will definitely be run (this is the
     * last step in process initialization that can fail).
     *
     * @return <tt>true</tt> if the sections were successfully loaded.
     */
    protected boolean loadSections() {
        Lib.debug(dbgProcess, "UserProcess.loadSections()");

        if (numPages > Machine.processor().getNumPhysPages()) {
            coff.close();
            Lib.debug(dbgProcess, "\tinsufficient physical memory");
            return false;
        }
        Lib.assertTrue(pageTable != null);
        Lib.assertTrue(pageTable.length > 0);
        // load sections
        for (int s = 0; s < coff.getNumSections(); s++) {
            CoffSection section = coff.getSection(s);

            Lib.debug(dbgProcess,
                    "\tinitializing " + section.getName() + " section (" + section.getLength() + " pages)");

            for (int i = 0; i < section.getLength(); i++) {
                int vpn = section.getFirstVPN() + i;
                if (section.isReadOnly()) {
                    pageTable[vpn].readOnly = true;
                }
                // for now, just assume virtual addresses=physical addresses
                section.loadPage(i, pageTable[vpn].ppn);
            }
        }

        return true;
    }

    /**
     * Release any resources allocated by <tt>loadSections()</tt>.
     */
    protected void unloadSections() {
        Lib.debug(dbgProcess, "UserProcess.unloadSections()");

        UserKernel.pageLock.acquire();

        for (int i = 0; i < numPages; i++) {
            UserKernel.freePages.add(pageTable[i].ppn);
        }

        UserKernel.pageLock.release();

        coff.close();
    }

    /**
     * Initialize the processor's registers in preparation for running the program
     * loaded into this process. Set the PC register to point at the start function,
     * set the stack pointer register to point at the top of the stack, set the A0
     * and A1 registers to argc and argv, respectively, and initialize all other
     * registers to 0.
     */
    public void initRegisters() {
        Lib.debug(dbgProcess, "UserProcess.initRegisters()");

        Processor processor = Machine.processor();

        // by default, everything's 0
        for (int i = 0; i < processor.numUserRegisters; i++)
            processor.writeRegister(i, 0);

        // initialize PC and SP according
        processor.writeRegister(Processor.regPC, initialPC);
        processor.writeRegister(Processor.regSP, initialSP);

        // initialize the first two argument registers to argc and argv
        processor.writeRegister(Processor.regA0, argc);
        processor.writeRegister(Processor.regA1, argv);
    }

    /**
     * Handle the halt() system call.
     */
    private int handleHalt() {
        if (UserKernel.rootProcess != this)
            return 0;

        Machine.halt();
        Lib.assertNotReached("Machine.halt() did not halt machine!");
        return 0;
    }

    /**
     * Handle the creat() system call.
     * 
     * @param fileNameAddress
     * @return
     */
    private int handleCreate(int fileNameAddress) {
        String name = readVirtualMemoryString(fileNameAddress, maxFileNameLength);
        MyFileDescriptor fd = getFileDescriptor();
        if (fd == null)
            return -1;

        boolean result = fd.createFile(name);

        if (result)
            return fd.id;
        else
            return -1;
    }

    /**
     * Handle the open() system call.
     * 
     * @param fileNameAddress
     * @return
     */
    private int handleOpen(int fileNameAddress) {
        String name = readVirtualMemoryString(fileNameAddress, maxFileNameLength);
        MyFileDescriptor fd = getFileDescriptor();
        if (fd == null)
            return -1;

        boolean result = fd.openFile(name);

        if (result)
            return fd.id;
        else
            return -1;
    }

    /**
     * Handle read() system call.
     * 
     * @param descriptorId
     * @param buffeAddress
     * @param size
     * @return
     */
    private int handleRead(int descriptorId, int bufferAddress, int size) {
        MyFileDescriptor fd = getFileDescriptor(descriptorId);
        if (fd == null)
            return -1;

        if (size < 0) {
            Lib.debug(dbgProcess, "handleRead() - size of buffer is zero");
            return -1;
        }

        byte[] data = new byte[size];
        int readLength = fd.readFile(data, 0, size);
        if (readLength == -1) {
            Lib.debug(dbgProcess, "handleRead() - readFile() returned -1");
            return -1;
        }

        return writeVirtualMemory(bufferAddress, data, 0, readLength);
    }

    /**
     * Handle write() system call.
     * 
     * @param descriptorId
     * @param buffer
     * @param size
     * @return
     */
    private int handleWrite(int descriptorId, int bufferAddress, int size) {
        MyFileDescriptor fd = getFileDescriptor(descriptorId);
        if (fd == null)
            return -1;

        if (size < 0) {
            Lib.debug(dbgProcess, "handleWrite() - size of buffer is zero");
            return -1;
        }

        byte[] data = new byte[size];
        int readLength = readVirtualMemory(bufferAddress, data);
        if (readLength == -1) {
            Lib.debug(dbgProcess, "handleWrite() - readVirtualMemory() failed");
            return -1;
        }

        int writeLength = fd.writeFile(data, 0, size);
        if (writeLength == -1) {
            Lib.debug(dbgProcess, "handleWrite() - writeFile() returned -1");
            return -1;
        }

        return writeLength;
    }

    /**
     * Handle close() sytem call.
     * 
     * @param descriptorId
     * @return
     */
    private int handleClose(int descriptorId) {
        MyFileDescriptor fd = getFileDescriptor(descriptorId);
        if (fd == null)
            return -1;

        if (fd.closeFile())
            return 0;

        return -1;
    }

    /**
     * Handle unlink() system call.
     * 
     * @param fileNameAddress
     * @return
     */
    private int handleUnlink(int fileNameAddress) {
        String name = readVirtualMemoryString(fileNameAddress, maxFileNameLength);
        MyFileDescriptor fd = getFileDescriptor();
        if (fd == null)
            return -1;

        boolean result = fd.openFile(name);

        if (result == false)
            return -1;

        result = fd.unlinkFile();

        if (result == false)
            return -1;

        return 0;
    }

    private static final int syscallHalt = 0, syscallExit = 1, syscallExec = 2, syscallJoin = 3, syscallCreate = 4,
            syscallOpen = 5, syscallRead = 6, syscallWrite = 7, syscallClose = 8, syscallUnlink = 9;

    /**
     * Handle a syscall exception. Called by <tt>handleException()</tt>. The
     * <i>syscall</i> argument identifies which syscall the user executed:
     *
     * <table>
     * <tr>
     * <td>syscall#</td>
     * <td>syscall prototype</td>
     * </tr>
     * <tr>
     * <td>0</td>
     * <td><tt>void halt();</tt></td>
     * </tr>
     * <tr>
     * <td>1</td>
     * <td><tt>void exit(int status);</tt></td>
     * </tr>
     * <tr>
     * <td>2</td>
     * <td><tt>int  exec(char *name, int argc, char **argv);
     * 								</tt></td>
     * </tr>
     * <tr>
     * <td>3</td>
     * <td><tt>int  join(int pid, int *status);</tt></td>
     * </tr>
     * <tr>
     * <td>4</td>
     * <td><tt>int  creat(char *name);</tt></td>
     * </tr>
     * <tr>
     * <td>5</td>
     * <td><tt>int  open(char *name);</tt></td>
     * </tr>
     * <tr>
     * <td>6</td>
     * <td><tt>int  read(int fd, char *buffer, int size);
     *								</tt></td>
     * </tr>
     * <tr>
     * <td>7</td>
     * <td><tt>int  write(int fd, char *buffer, int size);
     *								</tt></td>
     * </tr>
     * <tr>
     * <td>8</td>
     * <td><tt>int  close(int fd);</tt></td>
     * </tr>
     * <tr>
     * <td>9</td>
     * <td><tt>int  unlink(char *name);</tt></td>
     * </tr>
     * </table>
     *
     * @param syscall the syscall number.
     * @param a0      the first syscall argument.
     * @param a1      the second syscall argument.
     * @param a2      the third syscall argument.
     * @param a3      the fourth syscall argument.
     * @return the value to be returned to the user.
     */
    public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
        switch (syscall) {
        case syscallHalt:
            return handleHalt();

        case syscallCreate:
            return handleCreate(a0);

        case syscallOpen:
            return handleOpen(a0);

        case syscallRead:
            return handleRead(a0, a1, a2);

        case syscallWrite:
            return handleWrite(a0, a1, a2);

        case syscallClose:
            return handleClose(a0);

        case syscallUnlink:
            return handleUnlink(a0);

        case syscallExit:
            return exit(a0);

        case syscallExec:
            return exec(a0, a1, a2);

        case syscallJoin:
            return join(a0, a1);

        default:
            Lib.debug(dbgProcess, "Unknown syscall " + syscall);
            Lib.assertNotReached("Unknown system call: " + syscall);
        }

        return 0;
    }

    private int join(int pID, int status) {
        UserProcess child = null;
        int children = this.childProcesses.size();

        // find process represented by processID
        for (int i = 0; i < children; i++) {
            if (this.childProcesses.get(i).processID == pID) {
                child = this.childProcesses.get(i);
                break;
            }
        }

        if (child == null) {
            return -1;
        }

        child.thread.join();

        // disown child from the list
        this.childProcesses.remove(child);
        child.parentProcess = null;

        hmLock.acquire();
        Integer istatus = Statuses.get(child.processID);
        hmLock.release();

        if (istatus == -9999) {
            return 0; // unhandle exception
        }

        // check child's status, to see what to return
        if (istatus != null) {
            byte[] buffer = new byte[4];
            Lib.bytesFromInt(buffer, 0, istatus);
            int bytesWritten = writeVirtualMemory(status, buffer);

            if (bytesWritten == 4) {
                return 1; // child exited normally
            }

            else {
                return 0;
            }

        }

        else {
            return 0; // really fucked up
        }

    }

    private int exit(int status) {
        if (parentProcess != null) {
            parentProcess.hmLock.acquire();
            parentProcess.Statuses.put(processID, status);
            parentProcess.hmLock.release();
        }

        this.unloadSections();
        ListIterator<UserProcess> iter = childProcesses.listIterator();

        while (iter.hasNext()) {
            iter.next().parentProcess = null;
        }

        childProcesses.clear();
        closeAllFileDescriptors();

        if (this.processID == 0) {
            Kernel.kernel.terminate(); // exit the root process
        }

        else {
            UThread.finish();
        }

        return status;
    }

    private int exec(int file, int argc, int argv) {

        String filename = readVirtualMemoryString(file, 256);

        if (filename == null) {
            return -1;
        }

        // initializations
        String[] fileString = filename.split("\\.");
        String[] arguments = new String[argc];



        // Meat and Potatoes
        UserProcess child = UserProcess.newUserProcess();

        if (child.execute(filename, arguments)) {
            this.childProcesses.add(child);
            child.parentProcess = this;
            return child.processID;
        }

        else {
            System.out.print("Cannot execute!!!");
            return -1;
        }
    }

    /**
     * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>.
     * The <i>cause</i> argument identifies which exception occurred; see the
     * <tt>Processor.exceptionZZZ</tt> constants.
     *
     * @param cause the user exception that occurred.
     */
    public void handleException(int cause) {
        Processor processor = Machine.processor();

        switch (cause) {
        case Processor.exceptionSyscall:
            int result = handleSyscall(processor.readRegister(Processor.regV0), processor.readRegister(Processor.regA0),
                    processor.readRegister(Processor.regA1), processor.readRegister(Processor.regA2),
                    processor.readRegister(Processor.regA3));
            processor.writeRegister(Processor.regV0, result);
            processor.advancePC();
            break;
        
        case Processor.exceptionTLBMiss:
            Lib.debug(dbgProcess, "UserProcess.handleException(), TLB miss");

            int badVAddr = processor.readRegister(Processor.regBadVAddr);
            int vpn = Processor.pageFromAddress(badVAddr);

            Lib.debug(dbgProcess, "UserProcess.handleException(), vpn: " + String.valueOf(vpn) + " V Adress: " + Lib.toHexString(badVAddr));

            if(vpn > pageTable.length) {
                Lib.debug(dbgProcess, "UserProcess.handleException(), vpn is larger than page table size, vpn: " + String.valueOf(vpn) + " pt size: " + String.valueOf(pageTable.length));
                Lib.assertNotReached("Unexpected exception");
            }

            // use LRU to find which entry to replace with this one
            int writeIndex = 0;
            int tlbSize = processor.getTLBSize();
            TranslationEntry entry;
            pageTable[vpn].used = true;

            for(int index = 0; index < tlbSize; index++) {
                entry = processor.readTLBEntry(index);

                if(entry == null) {
                    writeIndex = index;
                    break;
                } else if(entry.used == false) {
                    writeIndex = index;
                    break;
                } else if(index + 1 == tlbSize) {
                    Random rand = new Random();
                    writeIndex = rand.nextInt(tlbSize);
                }
            }

            processor.writeTLBEntry(writeIndex, pageTable[vpn]);
            break;

        default:
            Lib.debug(dbgProcess, "Unexpected exception: " + Processor.exceptionNames[cause]);
            Lib.assertNotReached("Unexpected exception");
        }
    }

    /**
     * Initialize file descriptor array and set the first two file descriptors, 0
     * and 1, to stdin and stdout;
     */
    private void initFileDescriptors() {
        descriptors = new MyFileDescriptor[fileDescriptorLimit];

        MyFileDescriptor stdin = new MyFileDescriptor(0);
        stdin.setAsConsoleInput();
        descriptors[0] = stdin;

        MyFileDescriptor stdout = new MyFileDescriptor(1);
        stdout.setAsConsoleOutput();
        descriptors[1] = stdout;
    }

    /**
     * Returns a file descriptor with the passed id or null if the id is invalid.
     * 
     * @param descriptorId
     * @return
     */
    private MyFileDescriptor getFileDescriptor(int descriptorId) {
        if (descriptorId > 15 || descriptorId < 0) {
            Lib.debug(dbgProcess, "getFileDescriptor() - invlaid file descriptor id: " + String.valueOf(descriptorId));
            return null;
        }

        MyFileDescriptor fd = descriptors[descriptorId];
        if (fd == null) {
            Lib.debug(dbgProcess, "getFileDescriptor() - file descriptor is null, id: " + String.valueOf(descriptorId));
            return null;
        }

        return fd;
    }

    /**
     * Returns an open file descriptor or creates one. If there are no open ones and
     * the limit is reached, this will return null.
     * 
     * @return
     */
    protected MyFileDescriptor getFileDescriptor() {
        MyFileDescriptor fd = null;
        for (int index = 0; index < fileDescriptorLimit; index++) {

            fd = descriptors[index];
            if (fd == null)
                return createFileDescriptor(index);
            if (fd.isOpen() == false)
                return fd;
        }

        Lib.debug(dbgProcess, "getFileDescriptor() - no open file descriptors");
        return null;
    }

    /**
     * Creates a file descriptor, gives an id and place it in the descriptors array
     * at that id.
     * 
     * @param id
     * @return
     */
    private MyFileDescriptor createFileDescriptor(int id) {
        MyFileDescriptor fd = new MyFileDescriptor(id);
        descriptors[id] = fd;
        return fd;
    }

    /**
     * Closes all open files, to be called when program closses properly.
     */
    private void closeAllFileDescriptors() {
        MyFileDescriptor fd;

        for (int index = 0; index < fileDescriptorLimit; index++) {

            fd = descriptors[index];
            if (fd == null)
                continue;
            if (fd.isOpen() == true)
                fd.closeFile();

        }
    }

    /** The program being run by this process. */
    protected Coff coff;

    /** This process's page table. */
    protected TranslationEntry[] pageTable;
    /** The number of contiguous pages occupied by the program. */
    protected int numPages;

    /** The number of pages in the program's stack. */
    protected final int stackPages = 8;

    private int initialPC, initialSP;
    private int argc, argv;
    private MyFileDescriptor[] descriptors;

    private static final int pageSize = Processor.pageSize;
    protected static final char dbgProcess = 'a';

    // The maximum length of for strings passed as arguments to syscalls is 256
    // bytes.
    private static final int fileNameByteSize = 256;
    private static final int maxFileNameLength = fileNameByteSize / 8;

    // A process can only have 16 open file descriptors concurrently.
    private static final int fileDescriptorLimit = 16;
    private static final int SIZE_OF_INT_IN_BYTES = 4;

    private static FileSystem fileSystem = ThreadedKernel.fileSystem;
    private static Vector<String> filesInUse = new Vector<String>();
    private static Vector<String> deleteBuffer = new Vector<String>();

    // new variables
    // exec variables
    private LinkedList<UserProcess> childProcesses;
    private UserProcess parentProcess;
    private int processID = 0;

    // exit variables
    private HashMap<Integer, Integer> Statuses;
    protected OpenFile stdin;
    protected OpenFile stdout;
    private Lock hmLock;

    // join variables
    private UThread thread;

    public class MyFileDescriptor {
        private OpenFile file;
        private boolean open;
        private int id;

        public MyFileDescriptor(int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }

        public boolean isOpen() {
            return open;
        }

        public boolean shouldDelete() {
            String name = file.getName();
            return deleteBuffer.contains(name);
        }

        public boolean shouldDelete(String fileName) {
            return deleteBuffer.contains(fileName);
        }

        public OpenFile getFile() {
            return file;
        }

        public boolean createFile(String name) {
            return openFile(name, true);
        }

        public boolean openFile(String fileName) {
            return openFile(fileName, false);
        }

        private boolean openFile(String name, boolean createIfNotExist) {
            if (validateFilename(name) == false)
                return false;
            if (shouldDelete(name) == true) {
                Lib.debug(dbgProcess, "openFile() - file marked for removal: " + name);
                return false;
            }

            OpenFile temp = ThreadedKernel.fileSystem.open(name, createIfNotExist);

            if (temp == null) {
                Lib.debug(dbgProcess, "openFile() - no such file: " + name);
                return false;
            }

            open = true;
            file = temp;
            filesInUse.add(name);

            return true;
        }

        public boolean setAsConsoleInput() {
            if (file != null)
                closeFile();

            file = UserKernel.console.openForReading();
            open = true;
            filesInUse.add(file.getName());
            return true;
        }

        public boolean setAsConsoleOutput() {
            if (file != null)
                closeFile();

            file = UserKernel.console.openForWriting();
            open = true;
            filesInUse.add(file.getName());
            return true;
        }

        public boolean closeFile() {
            if (file == null) {
                Lib.debug(dbgProcess, "closeFile() - file is null");
                return false;
            }
            if (open == false)
                return true;

            open = false;
            file.close();
            filesInUse.remove(file.getName());

            if (shouldDelete() == false)
                return true;

            deleteFile();
            return true;
        }

        public int readFile(int pos, byte[] buf, int offset, int length) {
            if (file == null) {
                Lib.debug(dbgProcess, "readFile() - file is null");
                return -1;
            }

            return file.read(pos, buf, offset, length);
        }

        public int readFile(byte[] buf, int offset, int length) {
            if (file == null) {
                Lib.debug(dbgProcess, "readFile() - file is null");
                return -1;
            }

            return file.read(buf, offset, length);
        }

        public int writeFile(int pos, byte[] buf, int offset, int length) {
            if (file == null) {
                Lib.debug(dbgProcess, "writeFile() - file is null");
                return -1;
            }

            int result = file.write(pos, buf, offset, length);
            if (result == -1 || result < length)
                return -1;

            return result;
        }

        public int writeFile(byte[] buf, int offset, int length) {
            return writeFile(file.tell(), buf, offset, length);
        }

        public boolean unlinkFile() {
            if (file == null) {
                Lib.debug(dbgProcess, "unlinkFile() - file is null");
                return false;
            }
            if (open)
                closeFile();

            return deleteFile();
        }

        private boolean deleteFile() {
            String name = file.getName();
            if (filesInUse.contains(name)) {

                if (deleteBuffer.contains(name) == false)
                    deleteBuffer.add(name);
                return false;

            }

            boolean result = fileSystem.remove(name);
            if (result && deleteBuffer.contains(name)) {
                file = null;
                deleteBuffer.remove(name);
            }

            return result;
        }

        private boolean validateFilename(String name) {
            return name.length() <= fileNameByteSize;
        }
    }

}
