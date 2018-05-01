package grails.test.mixin

import grails.persistence.Entity
import grails.test.mixin.webflow.WebFlowUnitTestMixin
import grails.testing.gorm.DataTest

class WebFlowUnitTestMixinTests extends WebFlowUnitTestMixin<MealController> implements DataTest {

    void setup() {
        mockWebFlowController(MealController)
        mockDomain(Meal)
    }

    void shouldEatBreakfastEggsFailValidation() {
        given: "We setup"
            def meal = new Meal()
            meal.eggs = true
            conversation.meal = meal
        when: "We perform the action"
            breakfastFlow.eatBreakfast.action()
        then: "We expect the following"
            'unableToEatBreakfast' == getTestState().stateTransition
            !conversation.meal.hotSauce
            conversation.meal.hasErrors()
    }

    void shouldTestFlowExecution() {
        when: "We execute the breakfast flow"
            breakfastFlow.init.action()
        then: "A meal should be present"
            conversation.meal != null
    }

    void shouldTestExecuteTransitionAction() {
        given: "We have a meal and we are feeling sick"
            def meal = new Meal()
            conversation.meal = meal

            params.reason = "Feeling Sick"
        when: "We trigger the action"
            def event = breakfastFlow.chooseMainDish.on.nothing.action()
        then: "All OK - exit the flow"
            event == 'success'
            'end' == getTestState().stateTransition
            params.reason == conversation.meal.skipReason
            conversation.meal.id
    }

    void shouldTestChooseMainDishTransitionNothingActionBadReason() {
        given: "We have a meal and we are feeling sick"
            def meal = new Meal()
            conversation.meal = meal

            params.reason = ""
        when: "We trigger the action"
            def event = breakfastFlow.chooseMainDish.on.nothing.action()
        then: "An error occurs"
            'error' == event
            'chooseMainDish' == getTestState().stateTransition
            !conversation.meal.skipReason
            !conversation.meal.id
    }

    void shouldTestSubflowArgs() {
        when: "We have a subflow"
            def subflow = breakfastFlow.prepareBacon.subflow
        then: "Args are passed as expected"
            'prepareBacon' == breakfastFlow.prepareBacon.subflowArgs.action
            'meal' == breakfastFlow.prepareBacon.subflowArgs.controller
            breakfastFlow.prepareBacon.subflowArgs.input.meal
    }

    void shouldTestFlowOutput() {
        when: "We trigger an output"
            flow.baconFlow = 'bacon'

            prepareBaconFlow.end.output()
        then: "The output is as expected"
            'bacon' == currentEvent.attributes.baconConst
            'bacon' == currentEvent.attributes.baconValue
            'bacon' == currentEvent.attributes.baconFlow
    }

    void shouldTestFlowInputDefault() {
        given: "We have some inputs"
            def inputParams = [
                    defaultBaconInput: 'bacon',
                    requiredWithoutValueBaconInput: 'bacon'
            ]
        when: "We trigger the flow with those"
            prepareBaconFlow.input(inputParams)
        then: "They are as expected"
            assert 'bacon' == flow.defaultBaconInput
            assert 'bacon' == flow.requiredWithoutValueBaconInput
    }

    void shouldTestFlowInputRequiredWithoutValue() {
        given: "We have some inputs but missing a required value"
            def inputParams = [
                    defaultBaconInput: 'bacon'
            ]
        when: "We attempt to start"
            prepareBaconFlow.input(inputParams)
        then: "Starting should fail"
            thrown MissingPropertyException
        when: "We have some inputs with a required value and we trigger again"
            inputParams = [
                    defaultBaconInput: 'bacon',
                    requiredWithoutValueBaconInput: 'bacon'
            ]
            prepareBaconFlow.input(inputParams)
        then: "All is well"
            'bacon' == flow.requiredWithoutValueBaconInput
    }

    void shouldTestFlowInputRequiredWithValue() {
        given: "We have some input params"
            def inputParams = [
                    requiredWithoutValueBaconInput: 'bacon'
            ]
        when: "We trigger the flow"
            prepareBaconFlow.input(inputParams)
        then: "The required input should be defaulted"
            'baconValue' == flow.requiredWithValueBaconInput
        when: "We have some input params and trigger the flow"
            inputParams = [
                    requiredWithoutValueBaconInput: 'bacon',
                    requiredWithValueBaconInput: 'bacon'
            ]
            prepareBaconFlow.input(inputParams)
        then: "The required input is taken from the input provided"
            'bacon' == flow.requiredWithValueBaconInput
    }

    void shouldTestFlowInputNotRequiredWithValue() {
        given: "We have input params"
            def inputParams = [
                    requiredWithoutValueBaconInput: 'bacon'
            ]
        when: "We trigger the flow"
            prepareBaconFlow.input(inputParams)
        then: "The input has the correct value"
            'baconValue' == flow.notRequiredWithValueBaconInput
        when: "We override the value"
            inputParams = [
                    requiredWithoutValueBaconInput: 'bacon',
                    notRequiredWithValueBaconInput: 'bacon'
            ]

            prepareBaconFlow.input(inputParams)
        then: "It takes the override"
            'bacon' == flow.notRequiredWithValueBaconInput
    }

    void shouldTestFlowInputClosure() {
        given: "We have flow vars setup"
            flow.closureBaconInputValue = 'bacon'

            def inputParams = [
                    requiredWithoutValueBaconInput: 'bacon',
            ]
        when: "We run the flow"
            prepareBaconFlow.input(inputParams)
        then: "The value is as expected"
            'bacon' == flow.closureBaconInput
    }

    void shouldTestFlowInputClosureValue() {
        given: "We have flow vars setup"
            flow.closureWithValueBaconInputValue = 'bacon'

            def inputParams = [
                    requiredWithoutValueBaconInput: 'bacon'
            ]
        when: "We run the flow"
            prepareBaconFlow.input(inputParams)
        then: "The value is as expected"
            'bacon' == flow.closureWithValueBaconInput
    }

    void shouldTestFlowInputValue() {
        given: "We have an input setup"
            def inputParams = [
                    requiredWithoutValueBaconInput: 'bacon'
            ]

        when: "We run the flow"
            prepareBaconFlow.input(inputParams)

        then: "The value is as expected"
            'baconValue' == flow.defaultValueBaconInput
        when: "We run with different values"
            inputParams = [
                    requiredWithoutValueBaconInput: 'bacon',
                    defaultValueBaconInput: 'bacon'
            ]

            prepareBaconFlow.input(inputParams)
        then: "The input is as expected"
            'bacon' == flow.defaultValueBaconInput

    }

    void shouldTestGeneralTransitionStructure() {
        expect: "All these transitions should yield the expected results"
            'chooseMainDish' == breakfastFlow.init.on.success.to
            'prepareEggs' == breakfastFlow.chooseMainDish.on.eggs.to
            'prepareToast' == breakfastFlow.chooseMainDish.on.toast.to
            'end' == breakfastFlow.chooseMainDish.on.nothing.to

            'eatBreakfast' == breakfastFlow.prepareEggs.on.success.to
            'chooseMainDish' == breakfastFlow.prepareEggs.on.errors.to

            'eatBreakfast' == breakfastFlow.prepareToast.on.success.to
            'chooseMainDish' == breakfastFlow.prepareToast.on.errors.to

            'beHappy' == breakfastFlow.eatBreakfast.on.success.to
            'chooseMainDish' == breakfastFlow.eatBreakfast.on.unableToEatBreakfast.to

            'end' == breakfastFlow.beHappy.on.done.to
            'chooseMainDish' == breakfastFlow.beHappy.on.eatMore.to

            !breakfastFlow.end.on
    }

}

@Entity
class Meal {
    String skipReason
    boolean eggs
    boolean hotSauce

    void skip(String reason) {
        skipReason = reason
    }

    static constraints = {
        skipReason blank:false
        hotSauce validator: { val, obj ->
            return !(val == false && obj.eggs == true)
        }
    }
}

class MealController {

    def mealService = [prepareForBreakfast:{ new Meal() }, addHotSauce:{ },addButter:{} ]

    def prepareEggsFlow = {
        crackEggs {
            on 'sucess' to 'end'
        }
        end()
    }
    def prepareToastFlow = {
        sliceBread {
            on 'sucess' to 'end'
        }
        end()
    }
     def prepareBaconFlow = {
        input {
            defaultBaconInput()
            requiredWithoutValueBaconInput required: true
            requiredWithValueBaconInput required: true, value: 'baconValue'
            notRequiredWithValueBaconInput required: false, value: 'baconValue'
            defaultValueBaconInput 'baconValue'
            closureBaconInput { flow.closureBaconInputValue }
            closureWithValueBaconInput value: { flow.closureWithValueBaconInputValue }
        }
        fryBacon {
            on 'sucess' to 'end'
        }
        end {
            output {
                baconConst("bacon")
                baconValue(value: "bacon")
                baconFlow { flow.baconFlow }
            }
        }
    }
    final breakfastFlow = {
        init {
            action {
                conversation.meal = mealService.prepareForBreakfast()
            }
            on('success').to('chooseMainDish')
        }

        chooseMainDish {
            on('eggs').to('prepareEggs')
            on('toast').to('prepareToast')
            on('bacon').to('prepareBacon')
            on('nothing') {
                conversation.meal.skip(params.reason)
                if (!conversation.meal.save()) {
                    return error()
                }
                return success()
            }.to('end')
        }

        prepareEggs {
            subflow(prepareEggsFlow)
            on('success').to('eatBreakfast')
            on('errors').to('chooseMainDish')
        }

        prepareToast{
            subflow(prepareToastFlow)
            on('success').to('eatBreakfast')
            on('errors').to('chooseMainDish')
        }

        prepareBacon{
            subflow(controller: 'meal', action: 'prepareBacon', input: [meal: new Meal()])
            on('success').to('eatBreakfast')
            on('errors').to('chooseMainDish')
        }

        eatBreakfast {
            action {
                def meal = conversation.meal
                if (meal.isEggs()) {
                    mealService.addHotSauce(meal)
                } else if (meal.isToast()) {
                    mealService.addButter(meal)
                }

                def valid = meal.validate()
                if (!valid) {
                    return unableToEatBreakfast()
                }
                return success()
            }
            on('success').to('beHappy')
            on('unableToEatBreakfast').to('chooseMainDish')
        }

        beHappy {
            on('done') {
                conversation.meal.save()
            }.to('end')
            on('eatMore').to('chooseMainDish')
        }

        end()
    }
}
