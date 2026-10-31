package com.strideflow.app.engine

import com.strideflow.app.model.DietaryPreference
import com.strideflow.app.model.NutritionAdvice
import com.strideflow.app.model.Workout
import com.strideflow.app.model.WorkoutType

object NutritionEngine {

    /**
     * Distance-tiered sports nutrition engine based on the four distance categories:
     * 1. 5K (Short Distance / ~20-30 min)
     * 2. 10K (Medium Distance / ~45-60+ min)
     * 3. Half Marathon (13.1 Miles / ~90+ min)
     * 4. Marathon & Ultra Distances (26.2+ Miles / 3+ hours)
     */
    fun getAdviceForWorkout(
        workout: Workout,
        runnerWeightKg: Double = 70.0,
        dietaryPreference: DietaryPreference = DietaryPreference.OMNIVORE
    ): NutritionAdvice {
        val distanceKm = workout.targetDistanceMeters / 1000.0

        return when {
            // Tier 4: Marathon & Ultra Distances (>= 25 km)
            distanceKm >= 25.0 || workout.type == WorkoutType.LONG_RUN && distanceKm >= 25.0 -> {
                NutritionAdvice(
                    preRunMealSummary = "Full carbohydrate-loading 2 to 3 days prior, reducing heavy fiber and fats. Race-day breakfast 3 to 4 hours before: familiar, high-carb, low-residue foods like white toast with honey/jam, a bagel, or a bowl of low-fiber cereal with a banana.",
                    preRunMealWindow = "3 to 4 hours before (Carb-load 2–3 days prior)",
                    preRunPrimerSummary = "Final glycogen top-up: energy chew or small sip of electrolyte-carb drink. Never try a new brand on long run or race day.",
                    preRunPrimerWindow = "30 to 45 minutes before start",
                    intraRunFueling = "Regular, scheduled fueling every 30 to 45 minutes (aiming for 60–75+ grams of carbs per hour via a mix of gels, chews, and electrolyte/carb drinks). Never try a new gel or brand on race day.",
                    postRunRecoveryCarbProtein = "Immediate liquid/easily absorbed nutrition (first 30 minutes) continuing over the next 24–48 hours: drinkable carbs and protein (protein shake with a banana or chocolate milk) if your stomach is locked up, transitioning to a balanced meal of complex carbs, quality protein, and fluids with sodium as appetite returns.",
                    postRunHydrationElectrolytes = "Aggressive electrolyte & fluid restoration: high-sodium broth, electrolyte tablets, and water to replace heavy sweat losses.",
                    recipeIdeas = getRecipesForDistance(dietaryPreference, Tier.MARATHON)
                )
            }

            // Tier 3: Half Marathon (15 km to < 25 km)
            distanceKm >= 15.0 || workout.type == WorkoutType.LONG_RUN && distanceKm >= 14.0 -> {
                NutritionAdvice(
                    preRunMealSummary = "A carbohydrate-rich meal 3 hours before start time. Oatmeal, a bagel with jam, or toast with honey—low in fat and fiber. (Begin gentle carb-loading the day before with rice, bread, or potatoes).",
                    preRunMealWindow = "3 hours before start time",
                    preRunPrimerSummary = "Small top-up 30–40 minutes before: half a banana or a small energy chew if needed.",
                    preRunPrimerWindow = "30 to 40 minutes prior",
                    intraRunFueling = "Fuel with 30–60 grams of carbohydrates per hour (starting around 45 minutes in) using energy gels, chews, or sports drinks—tested thoroughly during training. Sip water and electrolytes regularly.",
                    postRunRecoveryCarbProtein = "Immediate liquid carbs and protein within 30 to 60 minutes (like chocolate milk or a recovery shake), followed by a proper meal containing complex carbohydrates and lean protein (chicken, rice, vegetables) within a couple of hours.",
                    postRunHydrationElectrolytes = "Hydrate with water and electrolytes. Add salty snacks or broth if you lost heavy amounts of sodium during the run.",
                    recipeIdeas = getRecipesForDistance(dietaryPreference, Tier.HALF_MARATHON)
                )
            }

            // Tier 2: 10K (Medium Distance / ~45-60+ min / 7.5 km to < 15 km)
            distanceKm >= 7.5 -> {
                NutritionAdvice(
                    preRunMealSummary = "An easily digestible meal or snack like porridge (oatmeal) with a banana, or toast with jam or honey. Keep fiber, heavy fats, and excess protein low to prevent stomach cramps.",
                    preRunMealWindow = "1 to 2 hours prior",
                    preRunPrimerSummary = "Optional small top-up: a few bites of banana or a plain rice cake with honey.",
                    preRunPrimerWindow = "30 to 45 minutes before",
                    intraRunFueling = "Water or a small sip of sports drink if it is hot or you are pushing a hard pace.",
                    postRunRecoveryCarbProtein = "Within 30 to 60 minutes: A recovery snack or meal targeting a roughly 3:1 or 4:1 ratio of carbohydrates to protein—such as chocolate milk, a fruit-and-protein smoothie, or a turkey sandwich.",
                    postRunHydrationElectrolytes = "Rehydrate with 400–600 ml water. Add a pinch of sea salt or electrolyte tablet in hot conditions.",
                    recipeIdeas = getRecipesForDistance(dietaryPreference, Tier.TEN_K)
                )
            }

            // Tier 1: 5K (Short Distance / ~20-30 min / < 7.5 km)
            else -> {
                NutritionAdvice(
                    preRunMealSummary = "A light, carb-rich snack like a single banana, a piece of toast with a little honey, or a small handful of low-fiber food. Your body already has plenty of stored glycogen for a 5K.",
                    preRunMealWindow = "30 to 60 minutes prior",
                    preRunPrimerSummary = "Water only. Your stored glycogen is sufficient.",
                    preRunPrimerWindow = "15 to 30 minutes prior",
                    intraRunFueling = "Water only. No mid-run fuel required.",
                    postRunRecoveryCarbProtein = "Within 30 to 60 minutes: A standard, balanced meal or a light snack featuring carbohydrates and protein (such as yogurt with fruit or toast with peanut butter).",
                    postRunHydrationElectrolytes = "Rehydrate with 250–400 ml plain water.",
                    recipeIdeas = getRecipesForDistance(dietaryPreference, Tier.FIVE_K)
                )
            }
        }
    }

    private enum class Tier {
        FIVE_K,
        TEN_K,
        HALF_MARATHON,
        MARATHON
    }

    private fun getRecipesForDistance(diet: DietaryPreference, tier: Tier): List<String> {
        return when (diet) {
            DietaryPreference.OMNIVORE -> when (tier) {
                Tier.FIVE_K -> listOf(
                    "Pre: A single ripe banana or slice of white toast with honey",
                    "During: Water only",
                    "Post: Greek yogurt with berries or toast with peanut butter"
                )
                Tier.TEN_K -> listOf(
                    "Pre: Porridge (oatmeal) with sliced banana, or toast with strawberry jam",
                    "During: Water or occasional sip of electrolyte drink",
                    "Post: Chocolate milk, a fruit-and-whey smoothie, or turkey sandwich on sourdough"
                )
                Tier.HALF_MARATHON -> listOf(
                    "Pre: Large bowl of oatmeal with honey, or bagel with jam 3 hours prior; half banana 30 min prior",
                    "During: 30–60g carbs/hr via tested energy gels + water at aid stations",
                    "Post: Chocolate milk immediately, followed by grilled chicken breast, white rice, and salted vegetables"
                )
                Tier.MARATHON -> listOf(
                    "Pre: White toast with jam, plain bagel with honey, and bowl of cornflakes with banana",
                    "During: 60–75g carbs/hr every 30–45 min (energy gels + isotonic drink)",
                    "Post: Liquid whey isolate with banana immediately, followed by chicken/salmon rice bowl with bone broth"
                )
            }

            DietaryPreference.VEGETARIAN -> when (tier) {
                Tier.FIVE_K -> listOf(
                    "Pre: A ripe banana or toast with a drizzle of honey",
                    "During: Water only",
                    "Post: Cottage cheese with fruit or sourdough toast with peanut butter"
                )
                Tier.TEN_K -> listOf(
                    "Pre: Porridge with maple syrup and banana",
                    "During: Water or electrolyte sip if warm",
                    "Post: Chocolate milk or Greek yogurt smoothie with banana and honey"
                )
                Tier.HALF_MARATHON -> listOf(
                    "Pre: Oatmeal cooked in soy milk with honey, or plain bagel with fruit preserve",
                    "During: 30–60g carbs/hr via gels or chews with regular water sips",
                    "Post: Recovery shake (milk + protein), followed by sweet potato quinoa bowl with edamame"
                )
                Tier.MARATHON -> listOf(
                    "Pre: Plain bagel with jam, low-fiber cereal with banana and milk",
                    "During: 60–75g carbs/hr via energy chews and sports drink every 35 min",
                    "Post: High-protein chocolate milk immediately, followed by roasted sweet potatoes, tofu, and salted pretzels"
                )
            }

            DietaryPreference.VEGAN -> when (tier) {
                Tier.FIVE_K -> listOf(
                    "Pre: A ripe banana or slice of toast with maple syrup",
                    "During: Water only",
                    "Post: Soy yogurt with fruit, or whole grain toast with peanut butter"
                )
                Tier.TEN_K -> listOf(
                    "Pre: Porridge cooked in oat milk with banana and jam",
                    "During: Water or light electrolyte drink",
                    "Post: Chocolate oat milk or a plant-protein fruit smoothie (pea/rice protein)"
                )
                Tier.HALF_MARATHON -> listOf(
                    "Pre: Oatmeal with maple syrup, or bagel with jam; dates or half banana 30 min prior",
                    "During: 30–60g carbs/hr with 100% plant energy gels + water/electrolytes",
                    "Post: Plant protein shake immediately, followed by white jasmine rice, roasted tofu, and salted edamame"
                )
                Tier.MARATHON -> listOf(
                    "Pre: White bread toast with jam, bagel, or cream of rice with banana and maple syrup",
                    "During: 60–75g carbs/hr every 30–45 min with energy gels and carb beverage",
                    "Post: Tart cherry plant-protein smoothie immediately, followed by potato/rice hash with black beans and salted broth"
                )
            }

            DietaryPreference.GLUTEN_FREE -> when (tier) {
                Tier.FIVE_K -> listOf(
                    "Pre: A single banana or certified GF toast with a little honey",
                    "During: Water only",
                    "Post: Greek yogurt with sliced strawberries or GF toast with peanut butter"
                )
                Tier.TEN_K -> listOf(
                    "Pre: Certified GF oatmeal or cream of rice with a banana and jam",
                    "During: Water or electrolyte sports drink",
                    "Post: Chocolate milk, fruit smoothie, or turkey wrap in GF tortilla"
                )
                Tier.HALF_MARATHON -> listOf(
                    "Pre: GF porridge with honey or GF bagel with jam 3h prior; banana top-up 35 min prior",
                    "During: 30–60g carbs/hr via certified GF energy gels and electrolytes",
                    "Post: Recovery protein shake immediately, followed by grilled chicken, roasted sweet potatoes, and white rice"
                )
                Tier.MARATHON -> listOf(
                    "Pre: GF rice toast with honey/jam, or bowl of crispy rice cereal with banana and milk",
                    "During: 60–75g carbs/hr via tested GF gels and electrolyte hydration",
                    "Post: Drinkable protein shake with banana immediately, followed by salted salmon, white rice, and bone broth"
                )
            }

            DietaryPreference.LOW_FODMAP -> when (tier) {
                Tier.FIVE_K -> listOf(
                    "Pre: Firm (unripe) banana or sourdough spelt toast with a touch of maple syrup",
                    "During: Water only",
                    "Post: Lactose-free yogurt with blueberries or plain rice cakes with peanut butter"
                )
                Tier.TEN_K -> listOf(
                    "Pre: White rice porridge or oatmeal in water with firm banana and maple syrup",
                    "During: Plain water or low-FODMAP electrolyte fluid",
                    "Post: Lactose-free chocolate drink, or rice cakes with sliced turkey breast"
                )
                Tier.HALF_MARATHON -> listOf(
                    "Pre: White rice porridge with maple syrup; half firm banana 30 min before start",
                    "During: 30–60g carbs/hr using glucose/maltodextrin-based gels (avoiding high-fructose syrups)",
                    "Post: Lactose-free protein shake immediately, followed by grilled chicken, white potatoes, and spinach"
                )
                Tier.MARATHON -> listOf(
                    "Pre: Sourdough toast with strawberry jam, or cream of rice with firm banana",
                    "During: 60–75g carbs/hr of fructose-free energy gels and electrolyte drink",
                    "Post: Drinkable lactose-free protein shake immediately, followed by plain jasmine rice, chicken, and salted carrots"
                )
            }
        }
    }
}
