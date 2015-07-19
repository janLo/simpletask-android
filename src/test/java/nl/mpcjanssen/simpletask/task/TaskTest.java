package nl.mpcjanssen.simpletask.task;

import hirondelle.date4j.DateTime;
import junit.framework.TestCase;
import nl.mpcjanssen.simpletask.task.token.*;

import java.util.ArrayList;
import java.util.TimeZone;

/**
 * Created with IntelliJ IDEA.
 * User: Mark Janssen
 * Date: 21-7-13
 * Time: 12:28
 */

public class TaskTest extends TestCase {

    public void testEquals() {
        Task a = new Task( "Test abcd");
        Task b = new Task( "Test abcd");
        Task c = new Task( "Test abcd ");
        Task d = new Task("Test abcd");
        assertFalse(b.equals(c));
        assertFalse(b.equals(d));
        assertFalse(a.equals(b));
    }
    

    public void testParseIdemPotence() {
        String s = "Test abcd ";
        assertEquals(s,new Task(s).inFileFormat());
    }

    public void testWithoutCompletionInfo() {
        Task t = new Task( "(B) 2014-07-05 Test t:2014-07-05 rec:2d");
        assertEquals("(B) Test t:2014-07-05 rec:2d", t.getTextWithoutCompletionInfo());
    }

    public void testHidden() {
        assertTrue(!new Task("Test h:1").isVisible());
        assertFalse(!new Task("Test").isVisible());
        assertTrue(!new Task("h:1").isVisible());
    }

    public void testCompletion() {
        String rawText = "Test";
        Task t = new Task( rawText);
        DateTime completionDate = DateTime.today(TimeZone.getDefault());
        t.markComplete(completionDate);
        assertTrue(t.isCompleted());
        t.markIncomplete();
        assertFalse(t.isCompleted());
        assertEquals(rawText, t.inFileFormat());
    }

    public void testCompletionWithPrependDate() {
        String rawText = "Test";
        Task t = new Task( rawText, DateTime.today(TimeZone.getDefault()));
        rawText = t.inFileFormat();
        DateTime completionDate = DateTime.today(TimeZone.getDefault());
        t.markComplete(completionDate);
        assertTrue(t.isCompleted());
        t.markIncomplete();
        assertFalse(t.isCompleted());
        assertEquals(rawText, t.inFileFormat());

        t = new Task( "x 2000-01-01 2001-01-01 Test");
        assertEquals("2000-01-01", t.getCompletionDate());
        assertEquals("2001-01-01", t.getCreateDate());

        t = new Task( "x 2000-01-01 (A) 2001-01-01 Test");
        assertEquals("2000-01-01", t.getCompletionDate());
        assertEquals("2001-01-01", t.getCreateDate());
        assertEquals(Priority.A,t.getPriority());
    }

    public void testCompletionWithPriority1() {
        String rawText = "(A) Test";
        Task t = new Task( rawText);
        assertEquals(Priority.A, t.getPriority());
        ArrayList<Token> expectedTokens = new ArrayList<>();
        expectedTokens.add(new PRIO("(A) "));
        expectedTokens.add(new TEXT("Test"));
        assertEquals(expectedTokens, t.getTokens());
        DateTime completionDate = DateTime.today(TimeZone.getDefault());
        t.markComplete(completionDate);
        assertTrue(t.isCompleted());
        t.setPriority(Priority.B);
        t.markIncomplete();
        assertFalse(t.isCompleted());
        assertEquals(Priority.B , t.getPriority());
        assertEquals("(B) Test", t.inFileFormat());
    }

    public void testCompletionWithPriority2() {
        String rawText = "(A) Test";
        Task t = new Task( rawText);
        t.update(rawText);
        assertEquals(t.getPriority(), Priority.A);
        DateTime completionDate = DateTime.today(TimeZone.getDefault());
        t.markComplete(completionDate);
        assertTrue(t.isCompleted());
        t.markIncomplete();
        assertFalse(t.isCompleted());
        assertEquals(Priority.A , t.getPriority());
        assertEquals("(A) Test", t.inFileFormat());
    }
    public void testPriority() {
        Task t = new Task( "(C) Test");
        assertEquals(t.getPriority(), Priority.C);
        t.setPriority(Priority.A);
        assertEquals(t.getPriority(), Priority.A);
        t.setPriority(Priority.NONE);
        assertEquals(t.getPriority(), Priority.NONE);
        t = new Task( "Test");
        assertEquals(t.getPriority(), Priority.NONE);
        t.setPriority(Priority.A);
        assertEquals(t.getPriority(), Priority.A);
        assertEquals("(A) Test", t.inFileFormat());
        t.setPriority(Priority.NONE);
        assertEquals(t.getPriority(), Priority.NONE);
        assertEquals("Test", t.inFileFormat());
    }

    public void testCompletedPriority() {
        Task t = new Task("x 1111-11-11 (A) Test");
        ArrayList<Token> expectedTokens = new ArrayList<>();
        expectedTokens.add(new COMPLETED());
        expectedTokens.add(new COMPLETED_DATE("1111-11-11 "));
        expectedTokens.add(new PRIO("(A) "));
        expectedTokens.add(new TEXT("Test"));
        assertEquals(expectedTokens, t.getTokens());
        assertTrue(t.isCompleted());
        assertEquals(Priority.A,t.getPriority());
    }

    public void testRemoveTag() {
        Task t = new Task( "Milk @@errands");
        t.removeTag("@errands");
        assertEquals("Milk @@errands", t.inFileFormat());
        t.removeTag("@@errands");
        assertEquals("Milk", t.inFileFormat());
        assertEquals("Milk", t.showParts(Token.SHOW_ALL));
        t = new Task( "Milk @@errands +supermarket");
        t.removeTag("@@errands");
        assertEquals("Milk +supermarket", t.inFileFormat());
    }

    public void testRecurrence() {
        Task t1 = new Task( "Test");
        Task t2 = new Task( "Test rec:1d");
        assertEquals(null, t1.getRecurrencePattern());
        assertEquals("1d", t2.getRecurrencePattern());
        String t3 = "(B) 2014-07-05 Test t:2014-07-05 rec:2d";
        String t3a = "(B) 2014-07-05 Test t:2014-07-05 rec:+2d";
        Task t4 = new Task(t3).markComplete(DateTime.forDateOnly(2000,1,1));
        Task t5 = new Task(t3a).markComplete(DateTime.forDateOnly(2000, 1, 1));
        assertEquals("(B) 2000-01-01 Test t:2000-01-03 rec:2d", t4.inFileFormat());
        assertEquals("(B) 2000-01-01 Test t:2014-07-07 rec:+2d", t5.inFileFormat());

        String dt3 = "(B) 2014-07-05 Test due:2014-07-05 rec:2d";
        String dt3a = "(B) 2014-07-05 Test due:2014-07-05 rec:+2d";
        Task dt4 = new Task(dt3).markComplete(DateTime.forDateOnly(2000,1,1));
        Task dt5 = new Task(dt3a).markComplete(DateTime.forDateOnly(2000,1,1));
        assertEquals("(B) 2000-01-01 Test due:2000-01-03 rec:2d", dt4.inFileFormat());
        assertEquals("(B) 2000-01-01 Test due:2014-07-07 rec:+2d", dt5.inFileFormat());

        String text = "Test due:2014-07-05 rec:1y";
        Task task = new Task(text).markComplete(DateTime.forDateOnly(2000, 1, 1));
        assertEquals("Test due:2001-01-01 rec:1y", task.inFileFormat());
    }

    public void testDue() {
        Task t1 = new Task("Test");
        t1.setDueDate("2013-01-01");
        assertEquals("Test due:2013-01-01", t1.inFileFormat());
        // Don't add extra whitespace
        t1.setDueDate("2013-01-01");
        assertEquals("Test due:2013-01-01", t1.inFileFormat());
        // Don't leave behind whitespace
        t1.setDueDate("");
        assertEquals("Test", t1.inFileFormat());
    }

    public void testThreshold() {
        Task t1 = new Task( "t:2013-12-12 Test");
        Task t2 = new Task( "Test t:2013-12-12");
        ArrayList<Token> eTok = new ArrayList<Token>();
        eTok.add(new THRESHOLD_DATE("2013-12-12"));
        eTok.add(new WHITE_SPACE(" "));
        eTok.add(new TEXT("Test"));
        assertEquals(eTok, t1.getTokens());
        assertEquals("2013-12-12", t1.getThresholdDateString(""));
        assertEquals("2013-12-12", t2.getThresholdDateString(""));
        Task t3 = new Task( "Test");
        assertNull(t3.getThresholdDate());
        t3.setThresholdDate("2013-12-12");
        assertEquals("Test t:2013-12-12", t3.inFileFormat());
    }

    public void testInvalidThresholdDate() {
        Task t1 = new Task( "Test t:2013-11-31");
        assertFalse(t1.inFuture());
    }

    public void testInvalidDueDate() {
        Task t1 = new Task( "Test due:2013-11-31");
        assertEquals(null,t1.getDueDate());
    }

    public void testInvalidCreateDate() {
        Task t1 = new Task( "2013-11-31 Test");
        assertEquals("2013-11-31",t1.getRelativeAge(null));
    }

    public void testInvalidCompleteDate() {
        Task t1 = new Task( "x 2013-11-31 Test");
        assertEquals("2013-11-31",t1.getCompletionDate());
    }

    public void testParseText() {
        Task t1 = new Task( "abcd");
        ArrayList<Token> expected = new ArrayList<Token>();
        expected.add(new TEXT("abcd"));
        assertEquals(expected, t1.getTokens());
    }
}
