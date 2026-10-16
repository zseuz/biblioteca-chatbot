package com.biblioteca.chatbot.model;

/** Tipos de pregunta que el asistente sabe reconocer y responder. */
public enum Intent {
    /** «Hola», «buenas tardes». */
    GREETING,
    /** «Gracias». */
    THANKS,
    /** «Ayuda», «¿qué puedes hacer?». */
    HELP,
    /** Plazo de préstamo, máximo de préstamos, cuándo se renueva. */
    RULES,
    /** Peticiones de modificar datos (prestar, devolver…): el asistente solo consulta. */
    ACTION,
    /** Préstamos vencidos, opcionalmente de un usuario o libro concreto. */
    OVERDUE_LOANS,
    /** Préstamos en plazo (sin devolver y sin vencer). */
    ACTIVE_LOANS,
    /** Usuarios con más préstamos (activos o en total). */
    TOP_MEMBERS,
    /** Libros más prestados. */
    TOP_BOOKS,
    /** Préstamos por género. */
    GENRES,
    /** Totales generales (libros, usuarios, préstamos). */
    STATS,
    /** Catálogo completo o solo lo disponible. */
    LIST_BOOKS,
    /** Lista de usuarios. */
    LIST_MEMBERS,
    /** Préstamos de un usuario, o quién tiene un libro. */
    SUBJECT_LOANS,
    /** Buscar un libro por título, autor o género (disponibilidad incluida). */
    BOOK_SEARCH,
    /** No se reconoce la pregunta. */
    UNKNOWN
}
