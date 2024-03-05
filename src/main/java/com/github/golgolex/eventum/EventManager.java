package com.github.golgolex.eventum;

import com.github.golgolex.eventum.events.Event;
import com.github.golgolex.eventum.events.EventStoppable;

import java.io.Serial;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/*
 * Copyright 2024 eventum contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

public class EventManager {

    private static final Map<Class<? extends Event>, List<MethodData>> registry = new HashMap<>();

    private EventManager()
    {
    }

    public static void registerListener(final Object object)
    {
        for (var method : object.getClass().getDeclaredMethods())
        {
            if (isMethodBad(method)) continue;

            register(method, object);
        }
    }

    public static void registerListener(final Object object, final Class<? extends Event> eventClass)
    {
        for (var method : object.getClass().getDeclaredMethods())
        {
            if (isMethodBad(method, eventClass)) continue;

            register(method, object);
        }
    }

    public static void unregisterListener(final Object object)
    {
        for (var dataList : registry.values())
            dataList.removeIf(data -> data.source().equals(object));
        cleanMap(true);
    }

    public static void unregisterListener(final Object object, final Class<? extends Event> eventClass)
    {
        if (registry.containsKey(eventClass))
        {
            registry.get(eventClass).removeIf(data -> data.source().equals(object));
            cleanMap(true);
        }
    }

    private static void register(final Method method, final Object object)
    {
        final Class<? extends Event> indexClass = (Class<? extends Event>) method.getParameterTypes()[0];
        final MethodData data = new MethodData(object, method, method.getAnnotation(EventTarget.class).value());

        if (!data.target().isAccessible()) data.target().setAccessible(true);

        if (registry.containsKey(indexClass))
        {
            if (!registry.get(indexClass).contains(data))
            {
                registry.get(indexClass).add(data);
                sortListValue(indexClass);
            }
        } else
        {
            registry.put(indexClass, new CopyOnWriteArrayList<>() {
                @Serial
                private static final long serialVersionUID = 666L;

                {
                    this.add(data);
                }
            });
        }
    }

    public static void removeEntry(final Class<? extends Event> indexClass)
    {
        final Iterator<Map.Entry<Class<? extends Event>, List<MethodData>>> mapIterator = registry.entrySet().iterator();

        while (mapIterator.hasNext()) if (mapIterator.next().getKey().equals(indexClass))
        {
            mapIterator.remove();
            break;
        }
    }

    public static void cleanMap(final boolean onlyEmptyEntries)
    {
        final Iterator<Map.Entry<Class<? extends Event>, List<MethodData>>> mapIterator = registry.entrySet().iterator();

        while (mapIterator.hasNext())
            if (!onlyEmptyEntries || mapIterator.next().getValue().isEmpty()) mapIterator.remove();
    }

    private static void sortListValue(final Class<? extends Event> indexClass)
    {
        final List<MethodData> sortedList = new CopyOnWriteArrayList<>();

        for (final byte priority : Priority.VALUE_ARRAY)
            for (final MethodData data : registry.get(indexClass))
                if (data.priority() == priority) sortedList.add(data);

        registry.put(indexClass, sortedList);
    }

    private static boolean isMethodBad(final Method method)
    {
        return method.getParameterTypes().length != 1 || !method.isAnnotationPresent(EventTarget.class);
    }

    private static boolean isMethodBad(final Method method, final Class<? extends Event> eventClass)
    {
        return isMethodBad(method) || !method.getParameterTypes()[0].equals(eventClass);
    }

    public static void call(final Event event)
    {
        final List<MethodData> dataList = registry.get(event.getClass());

        if (dataList != null)
        {
            if (event instanceof final EventStoppable stoppable)
            {
                for (final MethodData data : dataList)
                {
                    invoke(data, event);

                    if (stoppable.isStopped()) break;
                }
            } else
            {
                for (final MethodData data : dataList) invoke(data, event);
            }
        }
    }

    private static void invoke(final MethodData data, final Event argument)
    {
        try
        {
            data.target().invoke(data.source(), argument);
        } catch (final IllegalAccessException | IllegalArgumentException | InvocationTargetException ignored)
        {
        }
    }

    private record MethodData(Object source, Method target, byte priority) {
    }
}
