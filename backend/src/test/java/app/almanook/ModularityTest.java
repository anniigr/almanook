package app.almanook;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModularityTest {

    static final ApplicationModules MODULES = ApplicationModules.of(AlmanookApplication.class);

    @Test
    void moduleBoundariesAreRespected() {
        MODULES.verify();
    }

    @Test
    void writeModuleDocumentation() {
        new Documenter(MODULES).writeModulesAsPlantUml().writeIndividualModulesAsPlantUml();
    }
}
