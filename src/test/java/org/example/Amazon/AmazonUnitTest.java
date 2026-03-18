package org.example.Amazon;

import org.example.Amazon.Cost.DeliveryPrice;
import org.example.Amazon.Cost.ExtraCostForElectronics;
import org.example.Amazon.Cost.ItemType;
import org.example.Amazon.Cost.PriceRule;
import org.example.Amazon.Cost.RegularCost;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Amazon project unit tests")
class AmazonUnitTest {

    @Nested
    @DisplayName("Amazon.java")
    class AmazonFileTests {

        @Nested
        @DisplayName("specification-based")
        class SpecificationBased {

            @Test
            void calculateAggregatesAllPriceRuleResults() {
                ShoppingCart cart = mock(ShoppingCart.class);
                PriceRule ruleOne = mock(PriceRule.class);
                PriceRule ruleTwo = mock(PriceRule.class);

                List<Item> items = List.of(new Item(ItemType.OTHER, "Book", 2, 10.0));
                when(cart.getItems()).thenReturn(items);
                when(ruleOne.priceToAggregate(items)).thenReturn(20.0);
                when(ruleTwo.priceToAggregate(items)).thenReturn(5.0);

                Amazon amazon = new Amazon(cart, List.of(ruleOne, ruleTwo));

                assertThat(amazon.calculate()).isEqualTo(25.0);
            }

            @Test
            void calculateReturnsZeroWhenNoRulesConfigured() {
                ShoppingCart cart = mock(ShoppingCart.class);
                Amazon amazon = new Amazon(cart, List.of());

                assertThat(amazon.calculate()).isEqualTo(0.0);
            }

            @Test
            void addToCartDelegatesToShoppingCart() {
                ShoppingCart cart = mock(ShoppingCart.class);
                Amazon amazon = new Amazon(cart, List.of());
                Item item = new Item(ItemType.ELECTRONIC, "Mouse", 1, 29.99);

                amazon.addToCart(item);

                verify(cart).add(item);
            }
        }

        @Nested
        @DisplayName("structural-based")
        class StructuralBased {

            @Test
            void calculateCallsCartRetrievalPerRuleInOrder() {
                ShoppingCart cart = mock(ShoppingCart.class);
                PriceRule ruleOne = mock(PriceRule.class);
                PriceRule ruleTwo = mock(PriceRule.class);
                List<Item> items = List.of(new Item(ItemType.OTHER, "Book", 1, 10.0));

                when(cart.getItems()).thenReturn(items);
                when(ruleOne.priceToAggregate(items)).thenReturn(10.0);
                when(ruleTwo.priceToAggregate(items)).thenReturn(5.0);

                Amazon amazon = new Amazon(cart, List.of(ruleOne, ruleTwo));
                amazon.calculate();

                verify(cart, times(2)).getItems();
                var order = inOrder(ruleOne, ruleTwo);
                order.verify(ruleOne).priceToAggregate(items);
                order.verify(ruleTwo).priceToAggregate(items);
            }

            @Test
            void calculateDoesNotQueryCartWhenNoRulesExist() {
                ShoppingCart cart = mock(ShoppingCart.class);
                Amazon amazon = new Amazon(cart, List.of());

                amazon.calculate();

                verifyNoInteractions(cart);
            }
        }
    }

    @Nested
    @DisplayName("Item.java")
    class ItemFileTests {

        @Nested
        @DisplayName("specification-based")
        class SpecificationBased {

            @Test
            void constructorValuesAreReturnedByGetters() {
                Item item = new Item(ItemType.ELECTRONIC, "Keyboard", 2, 49.99);

                assertThat(item.getType()).isEqualTo(ItemType.ELECTRONIC);
                assertThat(item.getName()).isEqualTo("Keyboard");
                assertThat(item.getQuantity()).isEqualTo(2);
                assertThat(item.getPricePerUnit()).isEqualTo(49.99);
            }
        }

        @Nested
        @DisplayName("structural-based")
        class StructuralBased {

            @Test
            void itemStoresProvidedZeroQuantityWithoutMutation() {
                Item item = new Item(ItemType.OTHER, "Notebook", 0, 3.5);

                assertThat(item.getQuantity()).isEqualTo(0);
                assertThat(item.getName()).isEqualTo("Notebook");
            }
        }
    }

    @Nested
    @DisplayName("RegularCost.java")
    class RegularCostFileTests {

        @Nested
        @DisplayName("specification-based")
        class SpecificationBased {

            @Test
            void regularCostUsesQuantityTimesUnitPrice() {
                RegularCost regularCost = new RegularCost();
                List<Item> cart = List.of(
                        new Item(ItemType.OTHER, "Book", 2, 10.0),
                        new Item(ItemType.ELECTRONIC, "Cable", 3, 5.0)
                );

                assertThat(regularCost.priceToAggregate(cart)).isEqualTo(35.0);
            }
        }

        @Nested
        @DisplayName("structural-based")
        class StructuralBased {

            @Test
            void regularCostReturnsZeroForEmptyCart() {
                RegularCost regularCost = new RegularCost();

                assertThat(regularCost.priceToAggregate(List.of())).isEqualTo(0.0);
            }
        }
    }

    @Nested
    @DisplayName("DeliveryPrice.java")
    class DeliveryPriceFileTests {

        @Nested
        @DisplayName("specification-based")
        class SpecificationBased {

            @Test
            void deliveryPriceReturnsFiveForOneToThreeItems() {
                DeliveryPrice deliveryPrice = new DeliveryPrice();
                List<Item> cart = List.of(
                        new Item(ItemType.OTHER, "A", 1, 1.0),
                        new Item(ItemType.OTHER, "B", 1, 1.0),
                        new Item(ItemType.OTHER, "C", 1, 1.0)
                );

                assertThat(deliveryPrice.priceToAggregate(cart)).isEqualTo(5.0);
            }

            @Test
            void deliveryPriceReturnsTwelvePointFiveForFourToTenItems() {
                DeliveryPrice deliveryPrice = new DeliveryPrice();
                List<Item> cart = List.of(
                        new Item(ItemType.OTHER, "1", 1, 1.0),
                        new Item(ItemType.OTHER, "2", 1, 1.0),
                        new Item(ItemType.OTHER, "3", 1, 1.0),
                        new Item(ItemType.OTHER, "4", 1, 1.0)
                );

                assertThat(deliveryPrice.priceToAggregate(cart)).isEqualTo(12.5);
            }

            @Test
            void deliveryPriceReturnsTwentyForMoreThanTenItems() {
                DeliveryPrice deliveryPrice = new DeliveryPrice();
                List<Item> cart = List.of(
                        new Item(ItemType.OTHER, "1", 1, 1.0),
                        new Item(ItemType.OTHER, "2", 1, 1.0),
                        new Item(ItemType.OTHER, "3", 1, 1.0),
                        new Item(ItemType.OTHER, "4", 1, 1.0),
                        new Item(ItemType.OTHER, "5", 1, 1.0),
                        new Item(ItemType.OTHER, "6", 1, 1.0),
                        new Item(ItemType.OTHER, "7", 1, 1.0),
                        new Item(ItemType.OTHER, "8", 1, 1.0),
                        new Item(ItemType.OTHER, "9", 1, 1.0),
                        new Item(ItemType.OTHER, "10", 1, 1.0),
                        new Item(ItemType.OTHER, "11", 1, 1.0)
                );

                assertThat(deliveryPrice.priceToAggregate(cart)).isEqualTo(20.0);
            }

            @Test
            void deliveryPriceReturnsTwelvePointFiveForExactlyTenItems() {
                DeliveryPrice deliveryPrice = new DeliveryPrice();
                List<Item> cart = List.of(
                        new Item(ItemType.OTHER, "1", 1, 1.0),
                        new Item(ItemType.OTHER, "2", 1, 1.0),
                        new Item(ItemType.OTHER, "3", 1, 1.0),
                        new Item(ItemType.OTHER, "4", 1, 1.0),
                        new Item(ItemType.OTHER, "5", 1, 1.0),
                        new Item(ItemType.OTHER, "6", 1, 1.0),
                        new Item(ItemType.OTHER, "7", 1, 1.0),
                        new Item(ItemType.OTHER, "8", 1, 1.0),
                        new Item(ItemType.OTHER, "9", 1, 1.0),
                        new Item(ItemType.OTHER, "10", 1, 1.0)
                );

                assertThat(deliveryPrice.priceToAggregate(cart)).isEqualTo(12.5);
            }
        }

        @Nested
        @DisplayName("structural-based")
        class StructuralBased {

            @Test
            void deliveryPriceReturnsZeroWhenCartIsEmpty() {
                DeliveryPrice deliveryPrice = new DeliveryPrice();

                assertThat(deliveryPrice.priceToAggregate(List.of())).isEqualTo(0.0);
            }

            @Test
            void deliveryPriceUsesCorrectBoundaryValues() {
                DeliveryPrice deliveryPrice = new DeliveryPrice();

                List<Item> threeItems = List.of(
                        new Item(ItemType.OTHER, "A", 1, 1.0),
                        new Item(ItemType.OTHER, "B", 1, 1.0),
                        new Item(ItemType.OTHER, "C", 1, 1.0)
                );
                List<Item> fourItems = List.of(
                        new Item(ItemType.OTHER, "A", 1, 1.0),
                        new Item(ItemType.OTHER, "B", 1, 1.0),
                        new Item(ItemType.OTHER, "C", 1, 1.0),
                        new Item(ItemType.OTHER, "D", 1, 1.0)
                );

                assertThat(deliveryPrice.priceToAggregate(threeItems)).isEqualTo(5.0);
                assertThat(deliveryPrice.priceToAggregate(fourItems)).isEqualTo(12.5);
            }
        }
    }

    @Nested
    @DisplayName("ExtraCostForElectronics.java")
    class ExtraCostForElectronicsFileTests {

        @Nested
        @DisplayName("specification-based")
        class SpecificationBased {

            @Test
            void returnsSurchargeWhenElectronicPresent() {
                ExtraCostForElectronics surchargeRule = new ExtraCostForElectronics();
                List<Item> cart = List.of(
                        new Item(ItemType.OTHER, "Notebook", 1, 4.0),
                        new Item(ItemType.ELECTRONIC, "Headphones", 1, 35.0)
                );

                assertThat(surchargeRule.priceToAggregate(cart)).isEqualTo(7.5);
            }

            @Test
            void returnsZeroWhenNoElectronicPresent() {
                ExtraCostForElectronics surchargeRule = new ExtraCostForElectronics();
                List<Item> cart = List.of(
                        new Item(ItemType.OTHER, "Pen", 1, 2.0),
                        new Item(ItemType.OTHER, "Notebook", 1, 4.0)
                );

                assertThat(surchargeRule.priceToAggregate(cart)).isEqualTo(0.0);
            }
        }

        @Nested
        @DisplayName("structural-based")
        class StructuralBased {

            @Test
            void returnsZeroForEmptyCart() {
                ExtraCostForElectronics surchargeRule = new ExtraCostForElectronics();

                assertThat(surchargeRule.priceToAggregate(List.of())).isEqualTo(0.0);
            }

            @Test
            void returnsSingleSurchargeEvenWithMultipleElectronicItems() {
                ExtraCostForElectronics surchargeRule = new ExtraCostForElectronics();
                List<Item> cart = List.of(
                        new Item(ItemType.ELECTRONIC, "Phone", 1, 500.0),
                        new Item(ItemType.ELECTRONIC, "Tablet", 1, 350.0)
                );

                assertThat(surchargeRule.priceToAggregate(cart)).isEqualTo(7.5);
            }
        }
    }
}
