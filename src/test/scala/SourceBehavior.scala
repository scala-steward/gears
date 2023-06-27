import concurrent.{Async, Future}
import concurrent.Async.{Listener, either}

import java.util.concurrent.CancellationException
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}
import scala.util.Random

class SourceBehavior extends munit.FunSuite {
  given ExecutionContext = ExecutionContext.global

  test("onComplete register after completion runs immediately") {
    var itRan = false
    Async.blocking:
      val f = Future.now(Success(10))
      f.onComplete({ _ =>
        itRan = true;
        true
      })
    assertEquals(itRan, true)
  }

  test("poll is asynchronous") {
    var itRan = false
    Async.blocking:
      val f = Future{Thread.sleep(50); 10}
      f.poll({_ => itRan = true; true})
      assertEquals(itRan, false)
  }

  test("onComplete is asynchronous") {
    var itRan = false
    Async.blocking:
      val f = Future {
        Thread.sleep(50); 10
      }
      f.onComplete({ _ => itRan = true; true })
      assertEquals(itRan, false)
  }

  test("await is synchronous") {
    var itRan = false
    Async.blocking:
      val f = Future {
        Thread.sleep(250);
        10
      }
      f.onComplete({ _ => itRan = true; true })
      Async.await(f)
      assertEquals(itRan, true)
  }

  test("sources wait on children sources when they block") {
    Async.blocking:
      val timeBefore = System.currentTimeMillis()
      val f = Future {
        Thread.sleep(50);
        Future {
          Thread.sleep(70)
          Future {
            Thread.sleep(20)
            10
          }.value
        }.value
      }.value
      val timeAfter = System.currentTimeMillis()
      assert(timeAfter - timeBefore >= 50 + 70 + 20)
  }

  test("sources do not wait on zombie sources (which are killed at the end of Async.Blocking)") {
    val timeBefore = System.currentTimeMillis()
    Async.blocking:
      val f = Future {
        Future { Thread.sleep(300) }
        1
      }.value
    val timeAfter = System.currentTimeMillis()
    assert(timeAfter - timeBefore < 290)
  }

  test("poll()") {
    Async.blocking:
      val f: Future[Int] = Future {
        Thread.sleep(100)
        1
      }
      assertEquals(f.poll(), None)
      Async.await(f)
      assertEquals(f.poll(), Some(Success(1)))
  }

  test("onComplete() fires") {
    Async.blocking:
      var aRan = false
      var bRan = false
      val f = Future{
        Thread.sleep(100)
        1
      }
      f.onComplete({_ => aRan = true; true})
      f.onComplete({_ => bRan = true; true})
      assertEquals(aRan, false)
      assertEquals(bRan, false)
      Async.await(f)
      assertEquals(aRan, true)
      assertEquals(bRan, true)
  }

  test("dropped onComplete() listener does not fire") {
    Async.blocking:
      var aRan = false
      var bRan = false
      val f = Future {
        Thread.sleep(100)
        1
      }
      val l: concurrent.Async.Listener[Try[Int]] = { _ => aRan = true; true }
      f.onComplete(l)
      f.onComplete({ _ => bRan = true; true })
      assertEquals(aRan, false)
      assertEquals(bRan, false)
      f.dropListener(l)
      Async.await(f)
      assertEquals(aRan, false)
      assertEquals(bRan, true)
  }

  test("map") {
    Async.blocking:
      val f: Future[Int] = Future{ 10 }
      assertEquals(Async.await(f.map({ case Success(i) => i + 1 })), 11)
      val g: Future[Int] = Future.now(Failure(AssertionError(1123)))
      assertEquals(Async.await(g.map({ case Failure(_) => 17 })), 17)
  }

  test("filter") {
    Async.blocking:
      val f: Future[Int] = Future { 10 }
      assertEquals(Async.await(f.filter({ case Success(i) => 0 == (i % 2) })), Success(10))
    // await when the filter predicate if false hangs forever
  }

  test("all listeners in chain fire") {
    Async.blocking:
      var aRan = false
      var bRan = false
      val f: Future[Int] = Future {
        Thread.sleep(50)
        10
      }
      val g = f.filter({ _ => true })
      f.onComplete({ _ => aRan = true; true})
      g.onComplete({ _ => bRan = true; true})
      assertEquals(aRan, false)
      assertEquals(bRan, false)
      Async.await(f)
      assertEquals(aRan, true)
      assertEquals(bRan, true)
  }

  test("either") {
    var touched = false
    Async.blocking:
      val f1 = Future{ Thread.sleep(300); touched = true; 10 }
      val f2 = Future{ Thread.sleep(50); 40 }
      val g = Async.await(either(f1, f2))
      assertEquals(g, Right(Success(40)))
      Thread.sleep(350)
      assertEquals(touched, true)
  }
}