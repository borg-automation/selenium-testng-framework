package io.github.borgautomation.dataproviders;

import org.testng.annotations.DataProvider;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class AddToCartDataProvider {

    private AddToCartDataProvider() {
    }

    @DataProvider(name = "addToCartData")
    public static Object[][] addToCartData() {
        List<Object[]> rows = new ArrayList<>();
        try (InputStream input = AddToCartDataProvider.class.getClassLoader()
                .getResourceAsStream("testdata/add-to-cart-products.csv")) {
            if (input == null) {
                throw new IllegalStateException("Unable to find testdata/add-to-cart-products.csv on the classpath");
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                reader.readLine(); // header row
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    String[] fields = line.split(",");
                    rows.add(new Object[]{fields[0], fields[1], Integer.parseInt(fields[2].trim())});
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load add-to-cart-products.csv", e);
        }
        return rows.toArray(new Object[0][]);
    }
}
