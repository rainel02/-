package com.pindou.app.config;

import com.pindou.app.model.*;
import com.pindou.app.repository.BeadProjectRepository;
import com.pindou.app.repository.InventoryRowRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner seedData(BeadProjectRepository projectRepository, InventoryRowRepository inventoryRowRepository) {
        return args -> {
            if (projectRepository.count() == 0) {
                BeadProject seed = new BeadProject();
                seed.setName("麦门永存");
                seed.setStatus(BeadStatus.IN_PROGRESS);
                seed.setTags(List.of("像素", "食物"));
                seed.setSourceUrl("https://example.com/pattern");
                seed.setQuantityDone(0);
                seed.setQuantityPlan(1);
                seed.setRequiredColors(new ArrayList<>(List.of(
                        new ColorRequirement("C11", 46),
                        new ColorRequirement("B22", 24),
                        new ColorRequirement("H16", 22)
                )));
                projectRepository.save(seed);
            }

        };
    }
}
