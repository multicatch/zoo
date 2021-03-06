package pl.cezaryregec.zoo.console;

import pl.cezaryregec.zoo.actions.*;
import pl.cezaryregec.zoo.console.annotation.ReadableName;
import pl.cezaryregec.zoo.console.deserializers.*;
import pl.cezaryregec.zoo.dto.result.ResultDto;
import pl.cezaryregec.zoo.exception.ShutdownRequestException;
import pl.cezaryregec.zoo.utils.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class AbstractConsoleAdapter<Actions extends ActionFactory, ActionIndex extends Enum<ActionIndex>> implements ConsoleAdapter<ActionIndex> {
    private static final List<Class<? extends DeserializationLink>> DESERIALIZATION_CHAIN = Stream.of(
            StringDeserializationLink.class,
            IntegerDeserializationLink.class,
            BooleanDeserializationLink.class,
            EnumDeserializationLink.class
    ).collect(Collectors.toList());

    private final Scanner SCANNER = new Scanner(System.in);
    protected final Actions actions;
    protected boolean isRunning = true;

    protected AbstractConsoleAdapter(Actions actions) {
        this.actions = actions;
    }

    public boolean isRunning() {
        return this.isRunning;
    }

    public void shutDown() {
        this.isRunning = false;
        SCANNER.close();
    }

    public abstract void nextCommand();

    public void execute(ActionIndex index) {
        if (!isRunning) {
            throw new IllegalStateException("Console is shut down");
        }

        String executorMethodName = ActionExecutor.class.getDeclaredMethods()[0].getName();
        Class<?> parameterType = ActionExecutor.class.getDeclaredMethods()[0].getParameterTypes()[0];

        ActionExecutor actionExecutor = actions.create(index);
        Method[] declaredMethods = actionExecutor.getClass().getDeclaredMethods();
        Class<?> queryType = Stream.of(declaredMethods)
                .filter(method -> executorMethodName.equals(method.getName()) && method.getParameterTypes()[0] != parameterType)
                .map(method -> method.getParameterTypes()[0])
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Class of type " + actionExecutor.getClass() + " is not a valid ActionExecutor"));

        ActionQuery query = (ActionQuery) ReflectionUtils.createInstance(queryType);
        Field[] declaredFields = queryType.getDeclaredFields();

        for (Field field : declaredFields) {
            String name = field.getName();
            String options = createOptionsForField(field);
            if (field.isAnnotationPresent(ReadableName.class)) {
                name = field.getAnnotation(ReadableName.class).value();
            }

            System.out.print(name + options + ": ");
            ReflectionUtils.setField(field, query, readInput(field.getType()));
        }

        try {
            ResultDto resultDto = actionExecutor.execute(query);
            System.out.println(format(resultDto));
        } catch(ShutdownRequestException exit) {
            shutDown();
        }
    }

    private String createOptionsForField(Field field) {
        Class<?> type = field.getType();
        if (type.getSuperclass() == Enum.class) {
            List<String> constants = Stream.of(type.getDeclaredFields())
                    .map(Field::getName)
                    .filter(item -> !"$VALUES".equals(item))
                    .collect(Collectors.toList());

            return " (" + String.join(", ", constants) + ")";
        }
        return "";
    }

    protected <T> T readInput(Class<T> type) {
        String input = SCANNER.nextLine();
        for (Class<? extends DeserializationLink> linkClass : DESERIALIZATION_CHAIN) {
            DeserializationLink link = ReflectionUtils.getInstance(linkClass);
            Object result = link.deserialize(input, type);

            if (result != null) {
                return (T) result;
            }
        }

        throw new IllegalStateException(type + " type not supported");
    }

    protected String format(ResultDto resultDto) {
        if (resultDto != null) {
            return resultDto.toString();
        }
        return "";
    }
}
