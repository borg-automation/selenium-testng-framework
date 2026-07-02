package io.github.borgautomation.tests;

import io.github.borgautomation.pages.InventoryPage;
import io.github.borgautomation.pages.LoginPage;
import org.testng.Assert;
import org.testng.annotations.Test;

public class SauceDemoLoginTest extends BaseTest {

    private static final String VALID_USER = "standard_user";
    private static final String LOCKED_USER = "locked_out_user";
    private static final String PASSWORD = "secret_sauce";

    @Test
    public void validLogin_inventoryLoads() {
        LoginPage loginPage = new LoginPage(driver);
        InventoryPage inventoryPage = loginPage.loginAs(VALID_USER, PASSWORD);
        Assert.assertTrue(inventoryPage.isLoaded(), "Inventory page should load after a valid login");
    }

    @Test
    public void invalidLogin_showsError() {
        LoginPage loginPage = new LoginPage(driver);
        loginPage.loginAs("invalid_user", "wrong_password");
        Assert.assertTrue(loginPage.getErrorMessage().contains("Username and password do not match"),
                "Expected an invalid credentials error message");
    }

    @Test
    public void lockedOutUser_showsError() {
        LoginPage loginPage = new LoginPage(driver);
        loginPage.loginAs(LOCKED_USER, PASSWORD);
        Assert.assertTrue(loginPage.getErrorMessage().contains("locked out"),
                "Expected a locked-out user error message");
    }

    @Test
    public void validLogin_addToCart_cartBadgeUpdates() {
        LoginPage loginPage = new LoginPage(driver);
        InventoryPage inventoryPage = loginPage.loginAs(VALID_USER, PASSWORD);
        inventoryPage.addItemToCart("add-to-cart-sauce-labs-backpack");
        Assert.assertEquals(inventoryPage.getCartBadgeCount(), 1, "Cart badge should show 1 item after adding to cart");
    }
}
