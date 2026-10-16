import { Pipe, PipeTransform } from '@angular/core';

/** Un trozo de la respuesta: un párrafo o una lista de elementos. */
export type ReplyBlock = { kind: 'text'; text: string } | { kind: 'list'; items: string[] };

/**
 * Divide la respuesta del asistente (texto plano) en párrafos y listas:
 * - las líneas que empiezan por «- » o «• » seguidas forman una lista;
 * - una línea en blanco separa párrafos; las demás líneas se unen al párrafo en curso.
 *
 * Solo produce texto: la plantilla lo muestra con interpolación, nunca como HTML.
 */
export function toBlocks(reply: string): ReplyBlock[] {
  const blocks: ReplyBlock[] = [];
  let newParagraph = true;
  for (const raw of reply.split(/\r?\n/)) {
    const line = raw.trim();
    const item = /^[-•]\s+(.+)$/.exec(line);
    const last = blocks.at(-1);
    if (item) {
      if (last?.kind === 'list') {
        last.items.push(item[1]);
      } else {
        blocks.push({ kind: 'list', items: [item[1]] });
      }
      newParagraph = true;
    } else if (!line) {
      newParagraph = true;
    } else if (!newParagraph && last?.kind === 'text') {
      last.text += '\n' + line;
    } else {
      blocks.push({ kind: 'text', text: line });
      newParagraph = false;
    }
  }
  return blocks;
}

/** {@link toBlocks} como pipe puro: se recalcula solo cuando cambia el texto. */
@Pipe({ name: 'replyBlocks' })
export class ReplyBlocksPipe implements PipeTransform {
  transform(reply: string): ReplyBlock[] {
    return toBlocks(reply);
  }
}
