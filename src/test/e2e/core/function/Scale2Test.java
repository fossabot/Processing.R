
// Generated by hack/generate-e2e-test.py
package test.e2e.core.function;

import static org.junit.Assert.fail;
import org.junit.Test;
import test.e2e.core.E2eTestBase;

public class Scale2Test extends E2eTestBase {

  public Scale2Test() {
    coreCodeTemplate = "P3D <- \"processing.opengl.PGraphics3D\"\n\nsettings <- function() {\n    size(100, 100, P3D)\n}\n\ndraw <- function() {\n    noFill()\n    translate(width/2 + 12, height/2)\n    box(20, 20, 20)\n    scale(2.5, 2.5, 2.5)\n    box(20, 20, 20)\n    saveFrame(\"%s\")\n    exit()\n}\n";
    referenceURI = "https://processing.org/reference/images/scale_2.png";
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