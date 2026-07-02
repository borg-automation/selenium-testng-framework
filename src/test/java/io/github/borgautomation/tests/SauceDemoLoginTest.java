package io.github.borgautomation.tests;

import io.github.borgautomation.dataproviders.AddToCartDataProvider;
import io.github.borgautomation.dataproviders.LoginDataProvider;
import io.github.borgautomation.models.LoginTestData;
import io.github.borgautomation.pages.InventoryPage;
import io.github.borgautomation.pages.LoginPage;
import org.testng.Assert;
import org.testng.annotations.Test;

public class SauceDemoLoginTest extends BaseTest {

    private static final String VALID_USER = "standard_user";
    private static final String PASSWORD = "secret_sauce";

    // Replaces Session 1's three hardcoded per-user tests (validLogin_inventoryLoads,
    // invalidLogin_showsError, lockedOutUser_showsError) - same coverage plus problem_user,
    // driven by testdata/login-users.json instead of one branch per user.
    @Test(dataProvider = "loginData", dataProviderClass = LoginDataProvider.class)
    public void login(LoginTestData data) {
        LoginPage loginPage = new LoginPage(driver);
        InventoryPage inventoryPage = loginPage.loginAs(data.username, data.password);

        if (data.expectedOutcome == LoginTestData.Outcome.SUCCESS) {
            Assert.assertTrue(inventoryPage.isLoaded(),
                    "Expected '" + data.username + "' to reach the inventory page");
        } else {
            Assert.assertEquals(loginPage.getErrorMessage(), data.expectedMessage,
                    "Unexpected error message for '" + data.username + "'");
        }
    }

    @Test
    public void validLogin_addToCart_cartBadgeUpdates() {
        LoginPage loginPage = new LoginPage(driver);
        InventoryPage inventoryPage = loginPage.loginAs(VALID_USER, PASSWORD);
        inventoryPage.addItemToCart("add-to-cart-sauce-labs-backpack");
        Assert.assertEquals(inventoryPage.getCartBadgeCount(), 1, "Cart badge should show 1 item after adding to cart");
    }

    @Test(dataProvider = "addToCartData", dataProviderClass = AddToCartDataProvider.class)
    public void addToCart_variousProducts(String productName, String addToCartButtonId, int expectedCartBadgeCount) {
        LoginPage loginPage = new LoginPage(driver);
        InventoryPage inventoryPage = loginPage.loginAs(VALID_USER, PASSWORD);
        inventoryPage.addItemToCart(addToCartButtonId);
        Assert.assertEquals(inventoryPage.getCartBadgeCount(), expectedCartBadgeCount,
                "Cart badge mismatch after adding '" + productName + "'");
    }
}
