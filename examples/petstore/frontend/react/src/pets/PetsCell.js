import React from 'react';
import config from "../config";
import {Link} from "react-router-dom";
import {shape, string} from 'prop-types'

const PetsCell = ({pet}) =>
  <div className="col-sm pet-card" style={{backgroundImage: `url(${config.SERVER_URL}/images/${pet.image})`}}>
    <Link className="pet-link" to={`/pets/${pet.slug}`}>
      <div className='pet-header'>
        <h4>
          {pet.name}
        </h4>

      </div>
    </Link>
  </div>

PetsCell.propTypes = {
  pets: shape({
    image: string,
    slug: string,
    name: string
  })
}

export default PetsCell;
