
// Generated by hack/generate-e2e-test.py
package test.e2e.core.function;

import static org.junit.Assert.fail;
import org.junit.Test;
import test.e2e.core.E2eTestBase;

public class RotateZ0Test extends E2eTestBase {

  public RotateZ0Test() {
    coreCodeTemplate = "settings <- function() {\n    size(100, 100, P3D)\n}\n\ndraw <- function() {\n    translate(width/2, height/2)\n    rotateZ(PI/3)\n    rect(-26, -26, 52, 52)\n    saveFrame(\"%s\")\n    exit()\n}\n";
    referenceURI = "https://processing.org/reference/images/rotateZ_0.png";
  }

  @Test
  public void test() {
    try {
      defaultOperation();
    } catch (Exception exception) {
      System.err.println(exception);
      fail("Should not have thrown any exception");
    }
  }
}
